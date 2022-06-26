/*
 * This file is part of Bisq.
 *
 * Bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */
package bisq.bots;

import bisq.bots.table.builder.TableBuilder;
import bisq.proto.grpc.*;
import bisq.proto.grpc.GetTradesRequest.Category;
import io.grpc.StatusRuntimeException;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import protobuf.PaymentAccount;

import java.io.*;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static bisq.bots.BotUtils.*;
import static bisq.bots.table.builder.TableType.BSQ_BALANCE_TBL;
import static bisq.bots.table.builder.TableType.BTC_BALANCE_TBL;
import static bisq.proto.grpc.GetOfferCategoryReply.OfferCategory.BSQ_SWAP;
import static io.grpc.Status.*;
import static java.lang.String.format;
import static java.lang.System.exit;
import static java.lang.System.out;
import static java.math.RoundingMode.HALF_UP;
import static java.util.Objects.requireNonNull;

/**
 * Base gRPC API bot example class, providing convenient methods for most types of interaction with the API daemon.
 * <p>
 * Most gRPC exceptions from the API daemon should be handled by subclasses, with special exceptions made for
 * the API takeoffer method, which can fail for many reasons, not all of which throw a gRPC StatusRuntimeException.
 * TODO Study the problem with inconsistent API takeoffer error handling on both daemon and client, and try to
 *  come up with a better solution, i.e., all errors throw an exception instead of the current "failure reasons"
 *  derived from UI purposed TaskRunner errors that frequently occur during offer availability checks.
 */
@Slf4j
public abstract class AbstractBot {

    // Program args are saved in case they need to be forwarded to another bot instance.
    protected final String[] args;
    protected final String walletPassword;
    protected final String conf;
    protected final GrpcStubs grpcStubs;
    protected final boolean isDryRun;
    // This is an experimental option for simulating and automating protocol payment steps during bot development.
    // Be extremely careful in its use;  You do not want to "simulate" payments when API daemon is connected to mainnet.
    protected final boolean canSimulatePaymentSteps;

    // Bot's list of preferred trading peers (onion addresses).
    // The list is defined in the subclass' properties (or external conf) file.
    protected final List<String> preferredTradingPeers = new ArrayList<>();

    // Used during dry runs to track offers that would be taken.
    // This list should remain empty if super.dryRun = FALSE until bot can take multiple offers in one session.
    protected final List<OfferInfo> offersTaken = new ArrayList<>();

    protected final boolean canUseBash = getBashPath().isPresent();
    protected boolean isShutdown = false;

    protected final Supplier<String> defaultPropertiesFilename = () -> this.getClass().getSimpleName() + ".properties";
    protected final Supplier<TxFeeRateInfo> txFeeRates = this::getTxFeeRateInfo;
    protected final Supplier<Long> minimumTxFeeRate = () -> txFeeRates.get().getMinFeeServiceRate();
    protected final Supplier<Long> mostRecentTxFeeRate = () -> txFeeRates.get().getFeeServiceRate();

    // Constructor
    public AbstractBot(String[] args) {
        this.args = toArgsWithWalletPassword.apply(args);
        Config bisqClientOpts = new Config(this.args, defaultPropertiesFilename.get());
        this.walletPassword = bisqClientOpts.getWalletPassword();
        this.conf = bisqClientOpts.getConf();
        this.grpcStubs = new GrpcStubs(bisqClientOpts.getHost(), bisqClientOpts.getPort(), bisqClientOpts.getPassword());
        this.isDryRun = bisqClientOpts.isDryRun();
        this.canSimulatePaymentSteps = bisqClientOpts.isSimulatePaymentSteps();
    }

    public abstract void run();

    /**
     * Pings the API daemon with a getversion request.  Any gRPC StatusRuntimeException exception
     * from the daemon is fatal, resulting in an immediate Java runtime System.exit(1).
     *
     * @param startTime of the bot, for logging the bot's uptime
     */
    protected void pingDaemon(long startTime) {
        try {
            var now = new Date();
            var upTime = Duration.ofMillis(now.getTime() - startTime);
            log.info("Pinging API daemon.  Uptime: {} hours {} minutes {} seconds.",
                    upTime.toHoursPart(),
                    upTime.toMinutesPart(),
                    upTime.toSecondsPart());
            var request = GetVersionRequest.newBuilder().build();
            var reply = grpcStubs.versionService.getVersion(request);
            log.info("API daemon {} is available.", reply.getVersion());
        } catch (StatusRuntimeException grpcException) {
            log.error("Fatal Error: {}, daemon not available.  Shutting down bot.", toCleanErrorMessage.apply(grpcException));
            exit(1);
        }
    }

    /**
     * Return true if bot has a preferred trading peer list.
     */
    protected final Supplier<Boolean> iHavePreferredTradingPeers = () -> preferredTradingPeers.size() > 0;

    /**
     * Return true is the offer maker's onion address is configured as a preferred trading peer.
     */
    protected final Predicate<OfferInfo> isMakerPreferredTradingPeer = (offer) ->
            iHavePreferredTradingPeers.get()
                    && preferredTradingPeers.contains(offer.getOwnerNodeAddress());

    /**
     * Returns true if the given maximum tx fee rate is <= the most recent Bisq network fee rate.
     */
    protected final Predicate<Long> isBisqNetworkTxFeeRateLowEnough = (maxTxFeeRate) -> {
        var currentTxFeeRate = mostRecentTxFeeRate.get();
        if (currentTxFeeRate <= maxTxFeeRate) {
            log.info("Current tx fee rate: {} sats/byte.", currentTxFeeRate);
            return true;
        } else {
            log.warn("Current network tx fee rate ({} sats/byte) is too high, it must fall below"
                            + " configured max fee rate ({} sats/byte) before attempting to take an offer.",
                    currentTxFeeRate,
                    maxTxFeeRate);
            return false;
        }
    };

    /**
     * Loads the given List<String> from a Java Properties object containing a comma separated list of onion
     * addresses in hostname:port format, defined for property key "preferredTradingPeers".  Will throw a
     * fatal exception if the hostname:port pairs are not properly comma delimited.
     */
    protected final BiConsumer<Properties, List<String>> loadPreferredOnionAddresses = (properties, list) -> {
        var commaSeparatedValues = properties.getProperty("preferredTradingPeers");
        if (commaSeparatedValues == null || commaSeparatedValues.isBlank() || commaSeparatedValues.isEmpty()) {
            log.warn("Non-Fatal Error:  no preferred trading peers defined in config file.");
            return;
        }
        String[] onions = commaSeparatedValues.split(",");
        // Do simple validation of each address or throw a fatal exception.
        // The most likely problem is user not separating each onion address with a comma.
        Arrays.stream(onions).forEach(onion -> list.add(getValidatedPeerAddress(onion)));
    };

    /**
     * Return the name of the BTC network API daemon is currently connected to:  mainnet, testnet3, or regtest.
     *
     * @return String name of BTC network
     */
    protected String getNetwork() {
        try {
            var request = GetNetworkRequest.newBuilder().build();
            return grpcStubs.walletsService.getNetwork(request).getNetwork();
        } finally {
            // There is a 1 getnetwork call per second rate meter on the API daemon, and bots have reason to call
            // getnetwork many times in rapid succession because the API daemon could be restarted against mainnet or
            // regtest at any instant.  So, we force the bot to wait a second after making this call, to avoid a
            // StatusRuntimeException(PERMISSION_DENIED).
            sleep(1_000);
        }
    }

    /**
     * Return true if API daemon is currently connected to BTC mainnet network.
     *
     * @return boolean true if API daemon is currently connected to BTC mainnet network
     */
    protected boolean isConnectedToMainnet() {
        return getNetwork().equalsIgnoreCase("mainnet");
    }

    /**
     * Return true if bot has taken the offer during this session -- for dry runs only.
     */
    protected final Predicate<OfferInfo> isAlreadyTaken = (offer) ->
            offersTaken.stream().anyMatch(o -> o.getId().equals(offer.getId()));

    /**
     * Print a table of BSQ balance information.
     * <p>
     * An encrypted wallet must be unlocked before making this request;  this method will not unlock it for you.
     *
     * @param title to display above the table
     */
    protected void printBSQBalances(String title) {
        try {
            log.info(title);
            var bsqBalances = getBalances().getBsq();
            new TableBuilder(BSQ_BALANCE_TBL, bsqBalances).build().print(out);
        } catch (StatusRuntimeException grpcException) {
            log.warn(toCleanErrorMessage.apply(grpcException));
        }
    }

    /**
     * Print a table of BTC balance information.
     * <p>
     * An encrypted wallet must be unlocked before making this request;  this method will not unlock it for you.
     *
     * @param title to display above the table
     */
    protected void printBTCBalances(String title) {
        try {
            log.info(title);
            var btcBalances = getBalances().getBtc();
            new TableBuilder(BTC_BALANCE_TBL, btcBalances).build().print(out);
        } catch (StatusRuntimeException grpcException) {
            log.warn(toCleanErrorMessage.apply(grpcException));
        }
    }

    /**
     * Request BTC and BSQ wallet balance information.
     *
     * @return bisq.proto.grpc.BalancesInfo
     * @see <a href="https://bisq-network.github.io/slate/#rpc-method-getbalances">https://bisq-network.github.io/slate/#balancesinfohttps://bisq-network.github.io/slate/#rpc-method-getbalances</a>
     * @see <a href="https://bisq-network.github.io/slate/#balancesinfo">https://bisq-network.github.io/slate/#balancesinfo</a>
     */
    protected BalancesInfo getBalances() {
        // An encrypted wallet must be unlocked before making this request.
        var request = GetBalancesRequest.newBuilder().build();
        var reply = grpcStubs.walletsService.getBalances(request);
        return reply.getBalances();
    }

    /**
     * Sends a stop reqeust to the API daemon.
     *
     * @see <a href="https://bisq-network.github.io/slate/?java#service-shutdownserver">https://bisq-network.github.io/slate/?java#service-shutdownserver</a>
     */
    protected void stopDaemon() {
        var request = StopRequest.newBuilder().build();
        //noinspection ResultOfMethodCallIgnored
        grpcStubs.shutdownService.stop(request);
        log.info("API server shutdown request sent.");
    }

    /**
     * Locks an encrypted wallet.
     *
     * @throws NonFatalException      if API daemon is not ready to perform wallet operations, wallet is not encrypted,
     *                                or wallet is already locked.
     * @throws StatusRuntimeException if a fatal error occurs.
     * @see <a href="https://bisq-network.github.io/slate/?java#rpc-method-lockwallet">https://bisq-network.github.io/slate/?java#rpc-method-lockwallet</a>
     */
    protected void lockWallet() throws NonFatalException {
        try {
            var request = LockWalletRequest.newBuilder().build();
            //noinspection ResultOfMethodCallIgnored
            grpcStubs.walletsService.lockWallet(request);
            log.info("Wallet is locked.");
        } catch (StatusRuntimeException grpcException) {
            if (exceptionHasStatus.test(grpcException, UNAVAILABLE)) {
                // The API server will throw a gPRC exception if the server is not ready to take calls, or wallet is
                // not available (initialized), encrypted, or is already locked.  These cases are not fatal.
                throw new NonFatalException(toNonFatalErrorMessage.apply(grpcException));
            } else if (exceptionHasStatus.test(grpcException, FAILED_PRECONDITION)) {
                // If wallet is not encrypted, we got a FAILED_PRECONDITION status code.
                throw new NonFatalException(toNonFatalErrorMessage.apply(grpcException));
            } else if (exceptionHasStatus.test(grpcException, ALREADY_EXISTS)) {
                // If wallet is already locked, we got an ALREADY_EXISTS status code.
                throw new NonFatalException(toNonFatalErrorMessage.apply(grpcException));
            } else {
                // Else we throw the fatal, unexpected exception.
                throw grpcException;
            }
        }
    }

    /**
     * Unlocks a locked, encrypted wallet.
     *
     * @param walletPassword   encrypted wallet's password
     * @param timeoutInSeconds how long the wallet is to be unlocked
     * @throws NonFatalException      if API daemon is not ready to perform wallet operations, wallet is not encrypted,
     *                                or wallet is already unlocked.
     * @throws StatusRuntimeException if a fatal error occurs.
     * @see <a href="https://bisq-network.github.io/slate/?java#rpc-method-unlockwallet">https://bisq-network.github.io/slate/?java#rpc-method-unlockwallet</a>
     */
    protected void unlockWallet(String walletPassword, long timeoutInSeconds) throws NonFatalException {
        try {
            var request = UnlockWalletRequest.newBuilder()
                    .setPassword(walletPassword)
                    .setTimeout(timeoutInSeconds)
                    .build();
            //noinspection ResultOfMethodCallIgnored
            grpcStubs.walletsService.unlockWallet(request);
            log.info("Wallet is unlocked.");
        } catch (StatusRuntimeException grpcException) {
            // The API server will throw a gPRC exception if the server is not ready to take calls, or wallet is
            // not available (initialized), encrypted, or is already unlocked.  These cases are not fatal.
            if (exceptionHasStatus.test(grpcException, UNAVAILABLE)) {
                // If wallet is not yet initialized, we got an UNAVAILABLE status code.
                throw new NonFatalException(toNonFatalErrorMessage.apply(grpcException));
            } else if (exceptionHasStatus.test(grpcException, FAILED_PRECONDITION)) {
                // If wallet is not encrypted, we got a FAILED_PRECONDITION status code.
                throw new NonFatalException(toNonFatalErrorMessage.apply(grpcException));
            } else if (exceptionHasStatus.test(grpcException, ALREADY_EXISTS)) {
                // If wallet is already unlocked, we got an ALREADY_EXISTS status code.
                throw new NonFatalException(toNonFatalErrorMessage.apply(grpcException));
            } else {
                // Else we throw the fatal, unexpected exception.
                throw grpcException;
            }
        }
    }

    /**
     * Attempt to unlock the wallet for 1 second using the given password, and shut down the bot if the
     * password check fails for any reason.
     *
     * @param walletPassword String API daemon's wallet password
     */
    protected void validateWalletPassword(String walletPassword) {
        try {
            var request = UnlockWalletRequest.newBuilder()
                    .setPassword(walletPassword)
                    .setTimeout(1)
                    .build();
            //noinspection ResultOfMethodCallIgnored
            grpcStubs.walletsService.unlockWallet(request);
        } catch (StatusRuntimeException grpcException) {
            log.error("Wallet password check failed.");
            log.error((toCleanErrorMessage.apply(grpcException)));
            exit(1);
        }
    }

    /**
     * Returns PaymentAccount for given paymentAccountId, else throws an IllegalArgumentException.
     *
     * @param paymentAccountId Unique identifier of the payment account
     * @return protobuf.PaymentAccount
     * @see <a href="https://bisq-network.github.io/slate/?java#rpc-method-getpaymentaccounts">https://bisq-network.github.io/slate/?java#rpc-method-getpaymentaccounts</a>
     * @see <a href="https://bisq-network.github.io/slate/?java#paymentaccount">https://bisq-network.github.io/slate/?java#paymentaccount</a>
     */
    protected PaymentAccount getPaymentAccount(String paymentAccountId) {
        var request = GetPaymentAccountsRequest.newBuilder().build();
        var response = grpcStubs.paymentAccountsService.getPaymentAccounts(request);
        return response.getPaymentAccountsList().stream()
                .filter(p -> p.getId().equals(paymentAccountId))
                .findFirst().orElseThrow(() ->
                        new IllegalArgumentException(
                                format("Payment account with ID '%s' not found.", paymentAccountId)));
    }

    /**
     * Returns the default BSQ Swap PaymentAccount.
     *
     * @return protobuf.PaymentAccount
     * @see <a href="https://bisq-network.github.io/slate/?java#rpc-method-getpaymentaccounts">https://bisq-network.github.io/slate/?java#rpc-method-getpaymentaccounts</a>
     * @see <a href="https://bisq-network.github.io/slate/?java#paymentaccount">https://bisq-network.github.io/slate/?java#paymentaccount</a>
     */
    protected PaymentAccount getBsqSwapPaymentAccount() {
        var request = GetPaymentAccountsRequest.newBuilder().build();
        var response = grpcStubs.paymentAccountsService.getPaymentAccounts(request);
        var bsqSwapPaymentMethodId = BSQ_SWAP.name();
        return response.getPaymentAccountsList().stream()
                .filter(p -> p.getPaymentMethod().getId().equals(bsqSwapPaymentMethodId))
                .findFirst().orElseThrow(() ->
                        new IllegalArgumentException("Your default BSQ Swap payment account was not found."));
    }

    protected void validateTradeFeeCurrencyCode(String bisqTradeFeeCurrency) {
        var isValidCurrencyCode = bisqTradeFeeCurrency.equalsIgnoreCase("BSQ")
                || bisqTradeFeeCurrency.equalsIgnoreCase("BTC");
        if (!isValidCurrencyCode)
            throw new IllegalStateException(
                    format("Bisq trade fees must be paid in BSQ or BTC, not %s.", bisqTradeFeeCurrency));
    }

    /**
     * Verifies (1) the given PaymentAccount has a selected trade currency, and (2) is a fiat payment account or an
     * XMR payment account, else throws an IllegalStateException.  (The bot does not yet support BSQ Swaps.)
     */
    protected void validatePaymentAccount(PaymentAccount paymentAccount) {
        if (!paymentAccount.hasSelectedTradeCurrency())
            throw new IllegalStateException(
                    format("PaymentAccount with ID '%s' and name '%s' has no selected currency definition.",
                            paymentAccount.getId(),
                            paymentAccount.getAccountName()));

        var selectedCurrencyCode = paymentAccount.getSelectedTradeCurrency().getCode();

        // Hacky way to find out if this is an altcoin payment method, but there is no BLOCK_CHAINS proto enum or msg.
        boolean isBlockChainsPaymentMethod = paymentAccount.getPaymentMethod().getId().equals("BLOCK_CHAINS");
        if (isBlockChainsPaymentMethod && !isXmr.test(selectedCurrencyCode))
            throw new IllegalStateException(
                    format("This bot only supports fiat and monero (XMR) trading, not the %s altcoin.",
                            selectedCurrencyCode));

        if (isBsq.test(selectedCurrencyCode))
            throw new IllegalStateException("This bot does not support BSQ Swaps.");
    }

    /**
     * Returns an offer with the given offerId, else throws a gRPC StatusRuntimeException.
     *
     * @return bisq.proto.grpc.OfferInfo
     * @see <a href="https://bisq-network.github.io/slate/?java#rpc-method-getoffer">https://bisq-network.github.io/slate/?java#rpc-method-getoffer</a>
     * @see <a href="https://bisq-network.github.io/slate/?java#offerinfo">https://bisq-network.github.io/slate/?java#offerinfo</a>
     */
    protected OfferInfo getOffer(String offerId) {
        var request = GetOfferRequest.newBuilder()
                .setId(requireNonNull(offerId, "offerId cannot be null"))
                .build();
        var response = grpcStubs.offersService.getOffer(request);
        return response.getOffer();
    }

    /**
     * Returns a list of offers with the given direction (BUY|SELL) and currency code, or an empty list.
     *
     * @return List<bisq.proto.grpc.OfferInfo>
     * @see <a href="https://bisq-network.github.io/slate/?java#rpc-method-getoffers">https://bisq-network.github.io/slate/?java#rpc-method-getoffers</a>
     */
    protected List<OfferInfo> getOffers(String direction, String currencyCode) {
        var request = GetOffersRequest.newBuilder()
                .setDirection(requireNonNull(direction, "direction cannot be null").toUpperCase())
                .setCurrencyCode(requireNonNull(currencyCode, "currencyCode cannot be null").toUpperCase())
                .build();
        var response = grpcStubs.offersService.getOffers(request);
        return response.getOffersList();
    }

    /**
     * Takes a BSQ swap offer.  Throws an exception if one of various possible causes of failure is detected.
     *
     * @throws NonFatalException      if a failure to take the offer was detected.  The offer could have been
     *                                unavailable for one of many reasons, or the taker's wallet had insufficient BTC
     *                                to cover the trade.  Other problems could result in this exception being thrown,
     *                                including attempts to make takeoffer requests more than once per minute.
     *                                None of these are fatal errors for a bot, which can opt to not terminate itself.
     * @throws StatusRuntimeException if a fatal error occurred while attempting to take the offer.
     * @see <a href="https://bisq-network.github.io/slate/?java#rpc-method-takeoffer">https://bisq-network.github.io/slate/?java#rpc-method-takeoffer</a>
     */
    public void takeBsqSwapOffer(OfferInfo offer, long pollingInterval) throws NonFatalException {
        OfferTaker offerTaker = new OfferTaker(grpcStubs, offer, pollingInterval);
        // May throw fatal StatusRuntimeException, or NonFatalException.
        offerTaker.takeOffer();
        log.info("You took offer '{}';  waiting on swap completion.", offer.getId());
        offerTaker.waitForBsqSwapCompletion(); // Blocks until swap is completed, or times out.
    }

    /**
     * Takes a fiat or xmr offer.  Throws an exception if one of various possible causes of failure is detected.
     *
     * @throws NonFatalException      if a failure to take the offer was detected.  The offer could have been
     *                                unavailable for one of many reasons, or the taker's wallet had insufficient BTC
     *                                to cover the trade.  Other problems could result in this exception being thrown,
     *                                including attempts to make takeoffer requests more than once per minute.
     *                                None of these are fatal errors for a bot, which can opt to not terminate itself.
     * @throws StatusRuntimeException if a fatal error occurred while attempting to take the offer.
     * @see <a href="https://bisq-network.github.io/slate/?java#rpc-method-takeoffer">https://bisq-network.github.io/slate/?java#rpc-method-takeoffer</a>
     */
    public void takeV1ProtocolOffer(OfferInfo offer,
                                    PaymentAccount paymentAccount,
                                    String bisqTradeFeeCurrency,
                                    long pollingInterval) throws NonFatalException {
        OfferTaker offerTaker = new OfferTaker(grpcStubs,
                offer,
                paymentAccount,
                bisqTradeFeeCurrency,
                pollingInterval);
        // May throw fatal StatusRuntimeException, or NonFatalException.
        offerTaker.takeOffer();
        log.info("You took offer '{}';  waiting on new trade contract preparation.", offer.getId());
        offerTaker.waitForTradePreparation(); // Blocks until new trade is prepared, or times out.
    }

    /**
     * Return a trade with the given ID.
     * Use this method if you know the trade exists, and you want an exception thrown if not found.
     *
     * @param tradeId of the trade being requested
     * @return bisq.proto.grpc.TradeInfo
     * @see <a href="https://bisq-network.github.io/slate/?java#rpc-method-gettrade">https://bisq-network.github.io/slate/?java#rpc-method-gettrade</a>
     */
    protected TradeInfo getTrade(String tradeId) {
        var request = GetTradeRequest.newBuilder().setTradeId(tradeId).build();
        var response = grpcStubs.tradesService.getTrade(request);
        return response.getTrade();
    }

    /**
     * Return list of currently open, closed, or failed trades.
     *
     * @param category OPEN | CLOSED | FAILED
     * @return List<bisq.proto.grpc.TradeInfo>
     */
    protected List<TradeInfo> getTrades(Category category) {
        var request = GetTradesRequest.newBuilder().setCategory(category).build();
        var response = grpcStubs.tradesService.getTrades(request);
        return response.getTradesList();
    }

    /**
     * Print list of trade summaries to stdout.
     *
     * @param category category OPEN | CLOSED | FAILED
     */
    protected void printTradesSummary(Category category) {
        var trades = getTrades(category);
        BotUtils.printTradesSummary(category, trades);
    }

    /**
     * Print list of today's trade summaries to stdout.
     *
     * @param category category OPEN | CLOSED | FAILED
     */
    protected void printTradesSummaryForToday(Category category) {
        var midnightToday = BotUtils.midnightToday.get();
        var trades = getTrades(category).stream()
                .filter(t -> t.getDate() >= midnightToday)
                .collect(Collectors.toList());
        BotUtils.printTradesSummary(category, trades);
    }

    /**
     * Send a "payment started" message to the BTC seller.
     *
     * @param tradeId the trade's unique identifier
     */
    protected void confirmPaymentStarted(String tradeId) {
        var request = ConfirmPaymentStartedRequest.newBuilder()
                .setTradeId(tradeId)
                .build();
        //noinspection ResultOfMethodCallIgnored
        grpcStubs.tradesService.confirmPaymentStarted(request);
    }

    /**
     * Send a "payment received confirmation" message to the BTC buyer.
     *
     * @param tradeId the trade's unique identifier
     */
    protected void confirmPaymentReceived(String tradeId) {
        var request = ConfirmPaymentReceivedRequest.newBuilder()
                .setTradeId(tradeId)
                .build();
        //noinspection ResultOfMethodCallIgnored
        grpcStubs.tradesService.confirmPaymentReceived(request);
    }

    /**
     * Close a completed trade -- move it to trade history list.
     *
     * @param tradeId the trade's unique identifier
     */
    protected void closeTrade(String tradeId) {
        var request = CloseTradeRequest.newBuilder()
                .setTradeId(tradeId)
                .build();
        //noinspection ResultOfMethodCallIgnored
        grpcStubs.tradesService.closeTrade(request);
    }

    /**
     * Returns a BigDecimal representing the current market price, source from Bisq price feed services.
     *
     * @return BigDecimal the current market price
     * @see <a href="https://bisq-network.github.io/slate/?java#rpc-method-getmarketprice">https://bisq-network.github.io/slate/?java#rpc-method-getmarketprice</a>
     */
    protected BigDecimal getCurrentMarketPrice(String currencyCode) {
        var request = MarketPriceRequest.newBuilder()
                .setCurrencyCode(currencyCode)
                .build();
        var response = grpcStubs.priceService.getMarketPrice(request);
        var precision = isAltcoin.test(currencyCode) ? 8 : 4;
        return BigDecimal.valueOf(response.getPrice()).setScale(precision, HALF_UP);
    }

    /**
     * Returns a BigDecimal representing the 30-day, volume weighted average BSQ price in BTC.
     *
     * @return BigDecimal the 30-day average price
     * // TODO fix link below to api doc
     * @see <a href="https://bisq-network.github.io/slate/?java#rpc-method-getavgbsqprice">https://bisq-network.github.io/slate/?java#rpc-method-getavgbsqprice</a>
     * // TODO fix link above to api doc
     */
    protected BigDecimal get30DayAvgBsqPriceInBtc() {
        var request = GetAverageBsqTradePriceRequest.newBuilder()
                .setDays(30)
                .build();
        var response = grpcStubs.priceService.getAverageBsqTradePrice(request);
        return new BigDecimal(response.getPrice().getBtcPrice());
    }

    /**
     * Return a summary of BTC transaction fee rate information, including the Bisq network's most recently available
     * BTC tx fee rate, in sats/byte, and the user's custom tx fee rate, if set.
     * <p>
     *
     * @return bisq.proto.grpc.TxFeeRateInfo
     * @see <a href="https://bisq-network.github.io/slate/#txfeerateinfo">https://bisq-network.github.io/slate/#txfeerateinfo</a>
     */
    protected TxFeeRateInfo getTxFeeRateInfo() {
        var request = GetTxFeeRateRequest.newBuilder().build();
        var response = grpcStubs.walletsService.getTxFeeRate(request);
        return response.getTxFeeRateInfo();
    }

    protected void validatePollingInterval(long pollingInterval) {
        if (pollingInterval < 1_000)
            throw new IllegalStateException("Cannot poll offer-book faster than 1x per second.");
    }

    /**
     * Print information about offers taken during bot simulation.
     */
    protected void printDryRunProgress() {
        if (isDryRun && offersTaken.size() > 0) {
            log.info("You have \"taken\" {} offer(s) during dry run:", offersTaken.size());
            printOffersSummary(offersTaken);
        }
    }

    /**
     * Add offer to list of taken offers -- for dry runs only.
     */
    protected void addToOffersTaken(OfferInfo offer) {
        offersTaken.add(offer);
        printOfferSummary(offer);
        log.info("Did not actually take that offer during this simulation.");
    }

    /**
     * Stall the bot for the number of seconds represented by the given durationInMillis.
     * <p>
     * If the bot can use the system's bash command language interpreter, show the countdown in the terminal,
     * else log a "Will wake up in {} seconds" statement, and put the current thread to sleep.
     *
     * @param log              bot implementation's logger
     * @param durationInMillis number of milliseconds to stall
     */
    protected void runCountdown(Logger log, long durationInMillis) {
        var seconds = toSeconds.apply(durationInMillis).intValue();
        if (canUseBash) {
            showCountdown(seconds);
        } else {
            log.info("Will wake up in {} seconds. ", seconds);
            sleep(durationInMillis);
        }
    }

    /**
     * Lock the wallet, stop the API daemon, and terminate the bot with a non-zero status (error).
     */
    protected void shutdownAfterFatalError(String errorMessage) {
        isShutdown = true;
        try {
            lockWallet();
        } catch (NonFatalException ex) {
            log.warn(ex.getMessage());
        }
        log.error(errorMessage);
        sleep(5_000);
        log.error("Sending stop request to daemon.");
        stopDaemon();
        exit(1);
    }

    /**
     * Returns Properties object for this bot.
     *
     * @return Properties loaded from file specified in '--conf=path' program argument.
     */
    protected Properties loadConfigFile() {
        if (conf.equals(defaultPropertiesFilename.get()))
            return loadDefaultProperties();
        else
            return loadExternalProperties();
    }

    /**
     * Returns default properties file named 'this.getClass().getSimpleName() + ".properties"'.
     *
     * @return Properties loaded from file
     */
    private Properties loadDefaultProperties() {
        Properties properties = new java.util.Properties();
        try {
            var defaultFilename = defaultPropertiesFilename.get();
            properties.load(this.getClass().getClassLoader().getResourceAsStream(defaultFilename));
            log.info("Internal configuration loaded from {}.", defaultFilename);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
        return properties;
    }

    /**
     * Return a Properties object loaded from an optional conf file, specified by the --conf=path program argument.
     */
    private Properties loadExternalProperties() {
        var confFile = new File(conf);
        if (!confFile.exists())
            throw new IllegalStateException(format("Configuration file %s does not exist.", conf));

        InputStream is;
        try {
            is = new FileInputStream(confFile);
            Properties properties = new java.util.Properties();
            try {
                properties.load(is);
                log.info("External configuration loaded from {}.", confFile.getAbsolutePath());
                return properties;
            } catch (FileNotFoundException ignored) {
                // Cannot happen here.  Ignore FileNotFoundException because confFile.exists() == true.
            } catch (IOException ex) {
                // Not ignored because file can exist but fail to be loaded into Properties object.
                throw new IllegalStateException(ex);
            } finally {
                is.close();
            }
        } catch (FileNotFoundException ignored) {
            // Cannot happen here.  Ignore FileNotFoundException because confFile.exists() == true.
        } catch (IOException ex) {
            // Not ignored because may fail to create new FileInputStream(confFile).
            throw new IllegalStateException(ex);
        }
        throw new IllegalStateException(format("Could not load properties from %s.", conf));
    }
}
