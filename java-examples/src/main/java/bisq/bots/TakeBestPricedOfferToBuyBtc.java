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
import java.util.function.Predicate;
import java.util.function.Supplier;

import static bisq.bots.BotUtils.*;
import static java.lang.String.format;
import static java.lang.System.exit;
import static java.math.RoundingMode.HALF_UP;
import static protobuf.OfferDirection.BUY;
import static protobuf.OfferDirection.SELL;

/**
 * The TakeBestPricedOfferToBuyBtc bot waits for attractively priced BUY BTC offers to appear, takes the offers
 * (up to a maximum of configured {@link #maxTakeOffers}), then shuts down both the API daemon and itself (the bot),
 * to allow the user to start the desktop UI application and complete the trades.
 * <p>
 * The benefit this bot provides is freeing up the user time spent watching the offer book in the UI, waiting for the
 * right offer to take.  This bot increases the chance of beating the other nodes at taking the offer.
 * <p>
 * The disadvantage is that if the user takes offers with the API, she must complete the trades with the desktop UI.
 * This problem is due to the inability of the API to fully automate every step of the trading protocol.  Sending fiat
 * payments, and confirming their receipt, are manual activities performed outside the Bisq daemon and desktop UI.
 * Also, the API and the desktop UI cannot run at the same time.  Care must be taken to shut down one before starting
 * the other.
 * <p>
 * The criteria for determining which offers to take are defined in the bot's configuration file
 * TakeBestPricedOfferToBuyBtc.properties (located in project's src/main/resources directory).  The individual
 * configurations are commented in the existing TakeBestPricedOfferToBuyBtc.properties, which should be used as a
 * template for your own use case.
 * <p>
 * One possible use case for this bot is sell BTC for GBP:
 * <pre>
 *      Take a "Faster Payment (Santander)" offer to buy BTC with GBP at or above current market price if:
 *          the offer maker is a preferred trading peer,
 *          and the offer's BTC amount is between 0.10 and 0.25 BTC,
 *          and the current transaction mining fee rate is below 20 sats / byte.
 * </pre>
 * <p>
 * Another possible use case for this bot is to buy BTC with XMR.  (We might say "sell XMR for BTC", but we need to
 * remember that all Bisq offers are for buying or selling BTC.)
 * <pre>
 *      Take an offer to buy BTC with XMR at or above current market price if:
 *          the offer maker is a preferred trading peer,
 *          and the offer's BTC amount is between 0.50 and 1.00 BTC,
 *          and the current transaction mining fee rate is below 15 sats / byte.
 * </pre>
 * <p>
 * <pre>
 * Usage:  TakeBestPricedOfferToBuyBtc  --password=api-password --port=api-port \
 *                          [--conf=take-best-priced-offer-to-buy-btc.conf] \
 *                          [--dryrun=true|false]
 *                          [--simulate-regtest-payment=true|false]
 * </pre>
 */
@Slf4j
@Getter
public class TakeBestPricedOfferToBuyBtc extends AbstractBot {

    // Config file:  resources/TakeBestPricedOfferToBuyBtc.properties.
    private final Properties configFile;
    // Taker bot's payment account (if the configured paymentAccountId is valid).
    private final PaymentAccount paymentAccount;
    // Taker bot's payment account trading currency code (if the configured paymentAccountId is valid).
    private final String currencyCode;
    // Taker bot's min market price margin.  A takeable offer's price margin (%) must be >= minMarketPriceMargin (%).
    private final BigDecimal minMarketPriceMargin;
    // Taker bot's min BTC amount to buy (or sell in case of XMR).  A takeable offer's amount must be >= minAmount BTC.
    private final BigDecimal minAmount;
    // Taker bot's max BTC amount to buy (or sell in case of XMR).   A takeable offer's amount must be <= maxAmount BTC.
    private final BigDecimal maxAmount;
    // Taker bot's max acceptable transaction fee rate.
    private final long maxTxFeeRate;
    // Taker bot's trading fee currency code (BSQ or BTC).
    private final String bisqTradeFeeCurrency;
    // Maximum # of offers to take during one bot session (shut down bot after N swaps).
    private final int maxTakeOffers;

    // Offer polling frequency must be > 1000 ms between each getoffers request.
    private final long pollingInterval;

    // The # of BSQ swap offers taken during the bot session (since startup).
    private int numOffersTaken = 0;

    public TakeBestPricedOfferToBuyBtc(String[] args) {
        super(args);
        pingDaemon(new Date().getTime()); // Shut down now if API daemon is not available.
        this.configFile = loadConfigFile();
        this.paymentAccount = getPaymentAccount(configFile.getProperty("paymentAccountId"));
        this.currencyCode = paymentAccount.getSelectedTradeCurrency().getCode();
        this.minMarketPriceMargin = new BigDecimal(configFile.getProperty("minMarketPriceMargin"))
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
        validatePaymentAccount(paymentAccount);
        printBotConfiguration();

        while (!isShutdown) {
            if (!isBisqNetworkTxFeeRateLowEnough.test(maxTxFeeRate)) {
                runCountdown(log, pollingInterval);
                continue;
            }

            // Taker bot's getOffers(direction) request param.  For fiat offers, is BUY (BTC), for XMR offers, is SELL (BTC).
            String offerDirection = isXmr.test(currencyCode) ? SELL.name() : BUY.name();

            // Get all available and takeable offers, sorted by price ascending.
            // The list contains both fixed-price and market price margin based offers.
            var offers = getOffers(offerDirection, currencyCode).stream()
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
                        var highestPricedOffer = offers.get(0);
                        log.info("No acceptable offer found.  Closest possible candidate did not pass filters:");
                        takeCriteria.printOfferAgainstCriteria(highestPricedOffer);
                    });

            printDryRunProgress();
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
        if (isDryRun) {
            addToOffersTaken(offer);
            numOffersTaken++;
            maybeShutdownAfterSuccessfulTradeCreation();
        } else {
            // An encrypted wallet must be unlocked before calling takeoffer and gettrade.
            // Unlock the wallet for 5 minutes.  If the wallet is already unlocked,
            // this command will override the timeout of the previous unlock command.
            try {
                unlockWallet(walletPassword, 600);
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
                maybeShutdownAfterSuccessfulTradeCreation();
            } catch (NonFatalException nonFatalException) {
                handleNonFatalException(nonFatalException);
            } catch (StatusRuntimeException fatalException) {
                handleFatalException(fatalException);
            }
        }
    }

    /**
     * Log the non-fatal exception, and stall the bot if the NonFatalException has a stallTime value > 0.
     */
    private void handleNonFatalException(NonFatalException nonFatalException) {
        log.warn(nonFatalException.getMessage());
        if (nonFatalException.hasStallTime()) {
            long stallTime = nonFatalException.getStallTime();
            log.warn("A minute must pass between the previous and the next takeoffer attempt."
                            + "  Stalling for {} seconds before the next takeoffer attempt.",
                    toSeconds.apply(stallTime + pollingInterval));
            runCountdown(log, stallTime);
        } else {
            runCountdown(log, pollingInterval);
        }
    }

    /**
     * Log the fatal exception, and shut down daemon and bot.
     */
    private void handleFatalException(StatusRuntimeException fatalException) {
        log.error("", fatalException);
        shutdownAfterFailedTradePreparation();
    }

    /**
     * Lock the wallet, stop the API daemon, and terminate the bot.
     */
    private void maybeShutdownAfterSuccessfulTradeCreation() {
        if (!isDryRun) {
            try {
                lockWallet();
            } catch (NonFatalException ex) {
                log.warn(ex.getMessage());
            }
        }
        if (numOffersTaken >= maxTakeOffers) {
            isShutdown = true;

            if (canSimulatePaymentSteps) {
                log.info("Shutting down bot after {} successful simulated trades."
                                + "  API daemon will not be shut down.",
                        numOffersTaken);
                sleep(2_000);
            } else {
                log.info("Shutting down API daemon and bot after taking {} offers."
                                + "  Complete the trade(s) with the desktop UI.",
                        numOffersTaken);
                sleep(2_000);
                log.info("Sending stop request to daemon.");
                stopDaemon();
            }

            exit(0);

        } else {
            log.info("You have taken {} offers during this bot session.", numOffersTaken);
        }
    }

    /**
     * Lock the wallet, stop the API daemon, and terminate the bot with a non-zero status (error).
     */
    private void shutdownAfterFailedTradePreparation() {
        shutdownAfterFatalError("Shutting down API daemon and bot after failing to find new trade.");
    }

    /**
     * Return true is fixed-price offer's price >= the bot's min market price margin.  Allows bot to take a
     * fixed-priced offer if the price is >= {@link #minMarketPriceMargin} (%) of the current market price.
     */
    protected final BiPredicate<OfferInfo, BigDecimal> isFixedPriceGEMinMarketPriceMargin =
            (offer, currentMarketPrice) -> BotUtils.isFixedPriceGEMinMarketPriceMargin(
                    offer,
                    currentMarketPrice,
                    this.getMinMarketPriceMargin());

    /**
     * Return true if offer.amt >= bot.minAmt AND offer.amt <= bot.maxAmt (within the boundaries).
     *  TODO API's takeoffer needs to support taking offer's minAmount.
     */
    protected final Predicate<OfferInfo> isWithinBTCAmountBounds = (offer) ->
            BotUtils.isWithinBTCAmountBounds(offer, getMinAmount(), getMaxAmount());

    private void printBotConfiguration() {
        var configsByLabel = new LinkedHashMap<String, Object>();
        configsByLabel.put("Bot OS:", getOSName() + " " + getOSVersion());
        var network = getNetwork();
        configsByLabel.put("BTC Network:", network);
        configsByLabel.put("My Payment Account:", "");
        configsByLabel.put("\tPayment Account Id:", paymentAccount.getId());
        configsByLabel.put("\tAccount Name:", paymentAccount.getAccountName());
        configsByLabel.put("\tCurrency Code:", currencyCode);
        configsByLabel.put("Trading Rules:", "");
        configsByLabel.put("\tMax # of offers bot can take:", maxTakeOffers);
        configsByLabel.put("\tMax Tx Fee Rate:", maxTxFeeRate + " sats/byte");
        configsByLabel.put("\tMin Market Price Margin:", minMarketPriceMargin + "%");
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
        TakeBestPricedOfferToBuyBtc bot = new TakeBestPricedOfferToBuyBtc(args);
        bot.run();
    }

    /**
     * Calculates additional takeoffer criteria based on conf file values,
     * performs candidate offer filtering, and provides useful log statements.
     */
    private class TakeCriteria {
        private final BigDecimal currentMarketPrice;
        @Getter
        private final BigDecimal targetPrice;

        private final Supplier<String> marketDescription = () -> {
            if (isXmr.test(currencyCode))
                return "Buy XMR (Sell BTC)";
            else
                return "Buy BTC";
        };

        public TakeCriteria() {
            this.currentMarketPrice = getCurrentMarketPrice(currencyCode);
            this.targetPrice = calcTargetPrice(minMarketPriceMargin, currentMarketPrice, currencyCode);
        }

        /**
         * Returns the highest priced offer passing the filters, or Optional.empty() if not found.
         * Max tx fee rate filtering should have passed prior to calling this method.
         *
         * @param offers to filter
         */
        Optional<OfferInfo> findTakeableOffer(List<OfferInfo> offers) {
            if (iHavePreferredTradingPeers.get())
                return offers.stream()
                        .filter(o -> usesSamePaymentMethod.test(o, getPaymentAccount()))
                        .filter(isMakerPreferredTradingPeer)
                        .filter(o -> isMarginBasedPriceGETargetPrice.test(o, targetPrice)
                                || isFixedPriceGEMinMarketPriceMargin.test(o, currentMarketPrice))
                        .filter(isWithinBTCAmountBounds)
                        .findFirst();
            else
                return offers.stream()
                        .filter(o -> usesSamePaymentMethod.test(o, getPaymentAccount()))
                        .filter(o -> isMarginBasedPriceGETargetPrice.test(o, targetPrice)
                                || isFixedPriceGEMinMarketPriceMargin.test(o, currentMarketPrice))
                        .filter(isWithinBTCAmountBounds)
                        .findFirst();
        }

        void printCriteriaSummary() {
            if (isZero.test(minMarketPriceMargin)) {
                log.info("Looking for offers to {}, priced at or higher than the current market price {} {}.",
                        marketDescription.get(),
                        currentMarketPrice,
                        isXmr.test(currencyCode) ? "BTC" : currencyCode);
            } else {
                log.info("Looking for offers to {}, priced at or more than {}% {} the current market price {} {}.",
                        marketDescription.get(),
                        minMarketPriceMargin.abs(), // Hide the sign, text explains target price % "above or below".
                        aboveOrBelowMarketPrice.apply(minMarketPriceMargin),
                        currentMarketPrice,
                        isXmr.test(currencyCode) ? "BTC" : currencyCode);
            }
        }

        void printOffersAgainstCriteria(List<OfferInfo> offers) {
            log.info("Currently available {} offers -- want to take {} offer with price >= {} {}.",
                    marketDescription.get(),
                    currencyCode,
                    targetPrice,
                    isXmr.test(currencyCode) ? "BTC" : currencyCode);
            printOffersSummary(offers);
        }

        void printOfferAgainstCriteria(OfferInfo offer) {
            printOfferSummary(offer);

            var filterResultsByLabel = new LinkedHashMap<String, Object>();
            filterResultsByLabel.put("Current Market Price:", currentMarketPrice + " " + currencyCode);
            filterResultsByLabel.put("Target Price (Min):", targetPrice + " " + currencyCode);
            filterResultsByLabel.put("Offer Price:", offer.getPrice() + " " + currencyCode);
            filterResultsByLabel.put("Offer maker used same payment method?",
                    usesSamePaymentMethod.test(offer, getPaymentAccount()));
            filterResultsByLabel.put("Is offer maker a preferred trading peer?",
                    iHavePreferredTradingPeers.get()
                            ? isMakerPreferredTradingPeer.test(offer) ? "YES" : "NO"
                            : "N/A");
            var marginPriceLabel = format("Is offer's margin based price (%s) >= bot's target price (%s)?",
                    offer.getUseMarketBasedPrice() ? offer.getPrice() : "N/A",
                    offer.getUseMarketBasedPrice() ? targetPrice : "N/A");
            filterResultsByLabel.put(marginPriceLabel,
                    offer.getUseMarketBasedPrice()
                            ? isMarginBasedPriceGETargetPrice.test(offer, targetPrice)
                            : "N/A");
            var fixedPriceLabel = format("Is offer's fixed-price (%s) >= bot's target price (%s)?",
                    offer.getUseMarketBasedPrice() ? "N/A" : offer.getPrice() + " " + currencyCode,
                    offer.getUseMarketBasedPrice() ? "N/A" : targetPrice + " " + currencyCode);
            filterResultsByLabel.put(fixedPriceLabel,
                    offer.getUseMarketBasedPrice()
                            ? "N/A"
                            : isFixedPriceGEMinMarketPriceMargin.test(offer, currentMarketPrice));
            String btcAmountBounds = format("%s BTC - %s BTC", minAmount, maxAmount);
            filterResultsByLabel.put("Is offer's BTC amount within bot amount bounds (" + btcAmountBounds + ")?",
                    isWithinBTCAmountBounds.test(offer));

            var title = format("%s offer %s filter results:",
                    offer.getUseMarketBasedPrice() ? "Margin based" : "Fixed price",
                    offer.getId());
            log.info(toTable.apply(title, filterResultsByLabel));
        }
    }
}
