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

import bisq.proto.grpc.OfferInfo;
import io.grpc.StatusRuntimeException;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import protobuf.PaymentAccount;

import java.math.BigDecimal;
import java.util.*;
import java.util.function.BiPredicate;

import static bisq.bots.BotUtils.*;
import static java.lang.String.format;
import static java.math.RoundingMode.HALF_UP;
import static protobuf.OfferDirection.BUY;

/**
 * This bot's general use case is to buy XMR with your BTC at a low BTC price.  It periodically checks the
 * Sell XMR (Buy BTC) market, and takes a configured maximum number of offers to sell you XMR according to criteria you
 * define in the bot's configuration file:  <b>TakeBestPricedOfferToSellXmr.properties</b> (located in project's
 * src/main/resources directory).  You will need to replace the default values in the configuration file for your
 * use cases.
 * <p><br/>
 * After the maximum number of offers have been taken (good to start with 1), the bot will shut down the API daemon,
 * then itself.  You have to confirm the offer maker's XMR payment(s) outside Bisq, then complete the trade(s) in
 * the <a href="https://bisq.network">Bisq Desktop</a> application.
 * <p>
 * Here is one possible use case:
 * <pre>
 *  Take 1 offer to sell you XMR for BTC, priced no higher than 0.00% above or below current market price if:
 *
 *          the offer's BTC amount is between 0.50 and 1.00 BTC
 *          the offer maker is one of two preferred trading peers
 *          the current transaction mining fee rate is less than or equal 15 sats / byte
 *
 *  The bot configurations for these rules are set in TakeBestPricedOfferToSellXmr.properties as follows:
 *
 *          maxTakeOffers=1
 *          maxMarketPriceMargin=0.00
 *          minAmount=0.50
 *          maxAmount=1.00
 *          preferredTradingPeers=preferred-address-1.onion:9999,preferred-address-2.onion:9999
 *          maxTxFeeRate=15
 * </pre>
 * <b>Usage</b>
 * <p><br/>
 * You must encrypt your wallet password before running this bot.  If it is not already encrypted, you can use the CLI:
 * <pre>
 *     $ ./bisq-cli --password=xyz --port=9998 setwalletpassword --wallet-password="be careful"
 * </pre>
 * There are some {@link bisq.bots.Config program options} common to all the Java bot examples, passed on the command
 * line.  The only one you must provide (no default value) is your API daemon's password option:
 * `--password <String>`.  The bot will prompt you for your wallet-password in the console.
 * <p><br/>
 * You can pass the '--dryrun=true' option to the program to see what offers your bot <i>would take</i> with a given
 * configuration.  This will help you avoid taking offers by mistake.
 * <pre>
 *     TakeBestPricedOfferToSellXmr  --password=api-password --port=api-port [--dryrun=true|false]
 * </pre>
 * If your API daemon is running on a local regtest network (with a trading peer), you can pass the
 * '--simulate-regtest-payment=true' option to the program to simulate the full trade protocol.  The bot will print
 * your regtest trading peer's CLI commands in the console, for you to copy/paste into another terminal.
 * <pre>
 *     TakeBestPricedOfferToSellXmr  --password=api-password --port=api-port [--simulate-regtest-payment=true|false]
 * </pre>
 *
 * @see <a href="https://github.com/bisq-network/bisq-api-reference/blob/make-proto-downloader-runnable-from-any-dir/java-examples/src/main/java/bisq/bots/Config.java">bisq.bots.Config.java</a>
 */
@Slf4j
@Getter
public class TakeBestPricedOfferToSellXmr extends AbstractBot {

    // Taker bot's XMR payment account trading currency code.
    private static final String CURRENCY_CODE = "XMR";

    // Config file:  resources/TakeBestPricedOfferToSellXmr.properties.
    private final Properties configFile;
    // Taker bot's XMR payment account (if the configured paymentAccountId is valid).
    private final PaymentAccount paymentAccount;
    // Taker bot's maximum market price margin.  A takeable offer's price margin (%) must be <= maxMarketPriceMargin (%).
    private final BigDecimal maxMarketPriceMargin;
    // Taker bot's min BTC amount to trade.  A takeable offer's amount must be >= minAmount BTC.
    private final BigDecimal minAmount;
    // Taker bot's max BTC amount to trade.   A takeable offer's amount must be <= maxAmount BTC.
    private final BigDecimal maxAmount;
    // Taker bot's max acceptable transaction fee rate.
    private final long maxTxFeeRate;
    // Taker bot's trading fee currency code (BSQ or BTC).
    private final String bisqTradeFeeCurrency;
    // Maximum # of offers to take during one bot session (shut down bot after taking N offers).
    private final int maxTakeOffers;

    // Offer polling frequency must be > 1000 ms between each getoffers request.
    private final long pollingInterval;

    // The # of offers taken during the bot session (since startup).
    private int numOffersTaken = 0;

    public TakeBestPricedOfferToSellXmr(String[] args) {
        super(args);
        pingDaemon(new Date().getTime()); // Shut down now if API daemon is not available.
        this.configFile = loadConfigFile();
        this.paymentAccount = getPaymentAccount(configFile.getProperty("paymentAccountId"));
        this.maxMarketPriceMargin = new BigDecimal(configFile.getProperty("maxMarketPriceMargin"))
                .setScale(2, HALF_UP);
        this.minAmount = new BigDecimal(configFile.getProperty("minAmount"));
        this.maxAmount = new BigDecimal(configFile.getProperty("maxAmount"));
        this.maxTxFeeRate = Long.parseLong(configFile.getProperty("maxTxFeeRate"));
        this.bisqTradeFeeCurrency = configFile.getProperty("bisqTradeFeeCurrency");
        this.maxTakeOffers = Integer.parseInt(configFile.getProperty("maxTakeOffers"));
        loadPreferredOnionAddresses.accept(configFile, preferredTradingPeers);
        this.pollingInterval = Long.parseLong(configFile.getProperty("pollingInterval"));
    }

    /**
     * Checks for the most attractive offer to take every {@link #pollingInterval} ms.  After {@link #maxTakeOffers}
     * are taken, bot will stop the API daemon, then shut itself down, prompting the user to start the desktop UI
     * to complete the trade.
     */
    @Override
    public void run() {
        var startTime = new Date().getTime();
        validateWalletPassword(walletPassword);
        validatePollingInterval(pollingInterval);
        validateTradeFeeCurrencyCode(bisqTradeFeeCurrency);
        validatePaymentAccount(paymentAccount, CURRENCY_CODE);
        printBotConfiguration();

        while (!isShutdown) {
            if (!isBisqNetworkTxFeeRateLowEnough.test(maxTxFeeRate)) {
                runCountdown(log, pollingInterval);
                continue;
            }

            // Get all available and takeable buy BTC for XMR offers, sorted by price ascending.
            // The list may contain both fixed-price and market price margin based offers.
            var offers = getOffers(BUY.name(), CURRENCY_CODE).stream()
                    .filter(o -> !isAlreadyTaken.test(o))
                    .toList();

            if (offers.isEmpty()) {
                log.info("No takeable offers found.");
                runCountdown(log, pollingInterval);
                continue;
            }

            // Define criteria for taking an offer, based on conf file.
            TakeCriteria takeCriteria = new TakeCriteria();
            takeCriteria.printCriteriaSummary();
            takeCriteria.printOffersAgainstCriteria(offers);

            // Find takeable offer based on criteria.
            Optional<OfferInfo> selectedOffer = takeCriteria.findTakeableOffer(offers);
            // Try to take the offer, if found, or say 'no offer found' before going to sleep.
            selectedOffer.ifPresentOrElse(offer -> takeOffer(takeCriteria, offer),
                    () -> {
                        var cheapestOffer = offers.get(0);
                        log.info("No acceptable offer found.  Closest possible candidate did not pass filters:");
                        takeCriteria.printOfferAgainstCriteria(cheapestOffer);
                    });

            runCountdown(log, pollingInterval);
            pingDaemon(startTime);
        }
    }

    /**
     * Attempt to take the available offer according to configured criteria.  If successful, will block until a new
     * trade is fully initialized with a trade contract.  Otherwise, handles a non-fatal error and allows the bot to
     * stay alive, or shuts down the bot upon fatal error.
     */
    private void takeOffer(TakeCriteria takeCriteria, OfferInfo offer) {
        log.info("Will attempt to take offer '{}'.", offer.getId());
        takeCriteria.printOfferAgainstCriteria(offer);

        // An encrypted wallet must be unlocked before calling takeoffer and gettrade(s).
        // Unlock the wallet for 5 minutes.  If the wallet is already unlocked, this request
        // will override the timeout of the previous unlock request.
        try {
            unlockWallet(walletPassword, 300);
        } catch (NonFatalException nonFatalException) {
            handleNonFatalException(nonFatalException, pollingInterval);
        }

        if (isDryRun) {
            addToOffersTaken(offer);
            numOffersTaken++;
        } else {
            try {
                printBTCBalances("BTC Balances Before Take Offer Attempt");
                // Blocks until new trade is prepared, or times out.
                takeV1ProtocolOffer(offer, paymentAccount, bisqTradeFeeCurrency, pollingInterval);
                printBTCBalances("BTC Balances After Take Offer Attempt");

                if (canSimulatePaymentSteps) {
                    var newTrade = getTrade(offer.getId());
                    RegtestTradePaymentSimulator tradePaymentSimulator = new RegtestTradePaymentSimulator(args,
                            newTrade.getTradeId(),
                            paymentAccount);
                    tradePaymentSimulator.run();
                    printBTCBalances("BTC Balances After Simulated Trade Completion");
                }
                numOffersTaken++;
            } catch (NonFatalException nonFatalException) {
                handleNonFatalException(nonFatalException, pollingInterval);
            } catch (StatusRuntimeException fatalException) {
                shutdownAfterTakeOfferFailure(fatalException);
            }
        }
        maybeShutdownAfterSuccessfulTradeCreation(numOffersTaken, maxTakeOffers);
    }

    /**
     * Return true is fixed-price offer's price <= the bot's max market price margin.  Allows bot to take a
     * fixed-priced offer if the price is <= {@link #maxMarketPriceMargin} (%) of the current market price.
     */
    protected final BiPredicate<OfferInfo, BigDecimal> isFixedPriceLEMaxMarketPriceMargin =
            (offer, currentMarketPrice) -> BotUtils.isFixedPriceLEMaxMarketPriceMargin(
                    offer,
                    currentMarketPrice,
                    this.getMaxMarketPriceMargin());

    private void printBotConfiguration() {
        var configsByLabel = new LinkedHashMap<String, Object>();
        configsByLabel.put("Bot OS:", getOSName() + " " + getOSVersion());
        var network = getNetwork();
        configsByLabel.put("BTC Network:", network);
        configsByLabel.put("Dry Run?", isDryRun ? "YES" : "NO");
        configsByLabel.put("Simulate Regtest Trade?", canSimulatePaymentSteps ? "YES" : "NO");
        configsByLabel.put("My Payment Account:", "");
        configsByLabel.put("\tPayment Account Id:", paymentAccount.getId());
        configsByLabel.put("\tAccount Name:", paymentAccount.getAccountName());
        configsByLabel.put("\tCurrency Code:", CURRENCY_CODE);
        configsByLabel.put("Trading Rules:", "");
        configsByLabel.put("\tMax # of offers bot can take:", maxTakeOffers);
        configsByLabel.put("\tMax Tx Fee Rate:", maxTxFeeRate + " sats/byte");
        configsByLabel.put("\tMax Market Price Margin:", maxMarketPriceMargin + "%");
        configsByLabel.put("\tMin BTC Amount:", minAmount + " BTC");
        configsByLabel.put("\tMax BTC Amount: ", maxAmount + " BTC");
        if (iHavePreferredTradingPeers.get()) {
            configsByLabel.put("\tPreferred Trading Peers:", preferredTradingPeers.toString());
        } else {
            configsByLabel.put("\tPreferred Trading Peers:", "N/A");
        }
        configsByLabel.put("Bot Polling Interval:", pollingInterval + " ms");
        log.info(toTable.apply("Bot Configuration", configsByLabel));
    }

    public static void main(String[] args) {
        TakeBestPricedOfferToSellXmr bot = new TakeBestPricedOfferToSellXmr(args);
        bot.run();
    }

    /**
     * Calculates additional takeoffer criteria based on conf file values,
     * performs candidate offer filtering, and provides useful log statements.
     */
    private class TakeCriteria {
        private static final String MARKET_DESCRIPTION = "Sell XMR (Buy BTC)";

        private final BigDecimal currentMarketPrice;
        @Getter
        private final BigDecimal targetPrice;

        public TakeCriteria() {
            this.currentMarketPrice = getCurrentMarketPrice(CURRENCY_CODE);
            this.targetPrice = calcTargetPrice(maxMarketPriceMargin, currentMarketPrice, CURRENCY_CODE);
        }

        /**
         * Returns the lowest priced offer passing the filters, or Optional.empty() if not found.
         * The max tx fee rate filtering should have passed prior to calling this method.
         *
         * @param offers to filter
         */
        Optional<OfferInfo> findTakeableOffer(List<OfferInfo> offers) {
            if (iHavePreferredTradingPeers.get())
                return offers.stream()
                        .filter(o -> usesSamePaymentMethod.test(o, getPaymentAccount()))
                        .filter(isMakerPreferredTradingPeer)
                        .filter(o -> isMarginLEMaxMarketPriceMargin.test(o, maxMarketPriceMargin)
                                || isFixedPriceLEMaxMarketPriceMargin.test(o, currentMarketPrice))
                        .filter(o -> isWithinBTCAmountBounds(o, getMinAmount(), getMaxAmount()))
                        .findFirst();
            else
                return offers.stream()
                        .filter(o -> usesSamePaymentMethod.test(o, getPaymentAccount()))
                        .filter(o -> isMarginLEMaxMarketPriceMargin.test(o, maxMarketPriceMargin)
                                || isFixedPriceLEMaxMarketPriceMargin.test(o, currentMarketPrice))
                        .filter(o -> isWithinBTCAmountBounds(o, getMinAmount(), getMaxAmount()))
                        .findFirst();
        }

        void printCriteriaSummary() {
            if (isZero.test(maxMarketPriceMargin)) {
                log.info("Looking for offers to {}, priced at or lower than the current market price of {} BTC.",
                        MARKET_DESCRIPTION,
                        currentMarketPrice);
            } else {
                log.info("Looking for offers to {}, priced at or lower than {}% {} the current market price of {} BTC.",
                        MARKET_DESCRIPTION,
                        maxMarketPriceMargin.abs(), // Hide the sign, text explains target price % "above or below".
                        aboveOrBelowMaxMarketPriceMargin.apply(maxMarketPriceMargin),
                        currentMarketPrice);
            }
        }

        void printOffersAgainstCriteria(List<OfferInfo> offers) {
            log.info("Currently available {} offers -- want to take {} offer with price <= {} BTC.",
                    MARKET_DESCRIPTION,
                    CURRENCY_CODE,
                    targetPrice);
            printOffersSummary(offers);
        }

        void printOfferAgainstCriteria(OfferInfo offer) {
            printOfferSummary(offer);

            var filterResultsByLabel = new LinkedHashMap<String, Object>();
            filterResultsByLabel.put("Current Market Price:", currentMarketPrice + " " + CURRENCY_CODE);
            filterResultsByLabel.put("Target Price (Max):", targetPrice + " " + CURRENCY_CODE);
            filterResultsByLabel.put("Offer Price:", offer.getPrice() + " " + CURRENCY_CODE);
            filterResultsByLabel.put("Offer maker used same payment method?",
                    usesSamePaymentMethod.test(offer, getPaymentAccount()));
            filterResultsByLabel.put("Is offer maker a preferred trading peer?",
                    iHavePreferredTradingPeers.get()
                            ? isMakerPreferredTradingPeer.test(offer) ? "YES" : "NO"
                            : "N/A");

            if (offer.getUseMarketBasedPrice()) {
                var marginPriceLabel = format("Is offer's margin based price (%s) <= bot's target price (%s)?",
                        offer.getPrice() + " BTC",
                        targetPrice + " BTC");
                filterResultsByLabel.put(marginPriceLabel, isMarginLEMaxMarketPriceMargin.test(offer, maxMarketPriceMargin));
            } else {
                var fixedPriceLabel = format("Is offer's fixed-price (%s) <= bot's target price (%s)?",
                        offer.getPrice() + " BTC",
                        targetPrice + " BTC");
                filterResultsByLabel.put(fixedPriceLabel, isFixedPriceLEMaxMarketPriceMargin.test(offer, currentMarketPrice));
            }
            String btcAmountBounds = format("%s BTC - %s BTC", minAmount, maxAmount);
            filterResultsByLabel.put("Is offer's BTC amount within bot amount bounds (" + btcAmountBounds + ")?",
                    isWithinBTCAmountBounds(offer, getMinAmount(), getMaxAmount()));

            var title = format("%s offer %s filter results:",
                    offer.getUseMarketBasedPrice() ? "Margin based" : "Fixed price",
                    offer.getId());
            log.info(toTable.apply(title, filterResultsByLabel));
        }
    }
}
