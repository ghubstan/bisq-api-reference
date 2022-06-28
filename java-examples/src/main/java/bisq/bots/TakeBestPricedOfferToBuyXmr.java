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
import static protobuf.OfferDirection.SELL;

/**
 * The use case for the TakeBestPricedOfferToBuyXmr bot is to sell XMR for BTC at a high BTC price, e.g.:
 * <pre>
 *      Take an offer to buy XMR from you for BTC, at no less than #.##% above or below current market price if:
 *          the offer maker is a preferred trading peer,
 *          and the offer's BTC amount is between 0.50 and 1.00 BTC,
 *          and the current transaction mining fee rate is below 15 sats / byte.
 *
 * Usage:  TakeBestPricedOfferToBuyXmr  --password=api-password --port=api-port \
 *                          [--conf=take-best-priced-offer-to-buy-xmr.conf] \
 *                          [--dryrun=true|false]
 *                          [--simulate-regtest-payment=true|false]
 * </pre>
 * <p>
 * The criteria for determining which offers to take are defined in the bot's configuration file
 * TakeBestPricedOfferToBuyXmr.properties (located in project's src/main/resources directory).  The individual
 * configurations are commented in the existing TakeBestPricedOfferToBuyXmr.properties, which should be used as a
 * template for your own use case.
 */
@Slf4j
@Getter
public class TakeBestPricedOfferToBuyXmr extends AbstractBot {

    // Taker bot's XMR payment account trading currency code.
    private static final String CURRENCY_CODE = "XMR";

    // Config file:  resources/TakeBestPricedOfferToBuyXmr.properties.
    private final Properties configFile;
    // Taker bot's XMR payment account (if the configured paymentAccountId is valid).
    private final PaymentAccount paymentAccount;
    // Taker bot's minimum market price margin.  A takeable offer's price margin (%) must be >= minMarketPriceMargin (%).
    private final BigDecimal minMarketPriceMargin;
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

    public TakeBestPricedOfferToBuyXmr(String[] args) {
        super(args);
        pingDaemon(new Date().getTime()); // Shut down now if API daemon is not available.
        this.configFile = loadConfigFile();
        this.paymentAccount = getPaymentAccount(configFile.getProperty("paymentAccountId"));
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
        validatePaymentAccount(paymentAccount, CURRENCY_CODE);
        printBotConfiguration();

        while (!isShutdown) {
            if (!isBisqNetworkTxFeeRateLowEnough.test(maxTxFeeRate)) {
                runCountdown(log, pollingInterval);
                continue;
            }

            // Get all available and takeable sell BTC for XMR offers, sorted by price descending.
            // The list may contain both fixed-price and market price margin based offers.
            var offers = getOffers(SELL.name(), CURRENCY_CODE).stream()
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
            maybeShutdownAfterSuccessfulTradeCreation(numOffersTaken, maxTakeOffers);
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
                maybeShutdownAfterSuccessfulTradeCreation(numOffersTaken, maxTakeOffers);
            } catch (NonFatalException nonFatalException) {
                handleNonFatalException(nonFatalException, pollingInterval);
            } catch (StatusRuntimeException fatalException) {
                shutdownAfterTakeOfferFailure(fatalException);
            }
        }
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

    private void printBotConfiguration() {
        var configsByLabel = new LinkedHashMap<String, Object>();
        configsByLabel.put("Bot OS:", getOSName() + " " + getOSVersion());
        var network = getNetwork();
        configsByLabel.put("BTC Network:", network);
        configsByLabel.put("My Payment Account:", "");
        configsByLabel.put("\tPayment Account Id:", paymentAccount.getId());
        configsByLabel.put("\tAccount Name:", paymentAccount.getAccountName());
        configsByLabel.put("\tCurrency Code:", CURRENCY_CODE);
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
        TakeBestPricedOfferToBuyXmr bot = new TakeBestPricedOfferToBuyXmr(args);
        bot.run();
    }

    /**
     * Calculates additional takeoffer criteria based on conf file values,
     * performs candidate offer filtering, and provides useful log statements.
     */
    private class TakeCriteria {
        private static final String MARKET_DESCRIPTION = "Buy XMR (Sell BTC)";

        private final BigDecimal currentMarketPrice;
        @Getter
        private final BigDecimal targetPrice;

        public TakeCriteria() {
            this.currentMarketPrice = getCurrentMarketPrice(CURRENCY_CODE);
            this.targetPrice = calcTargetPrice(minMarketPriceMargin, currentMarketPrice, CURRENCY_CODE);
        }

        /**
         * Returns the highest priced offer passing the filters, or Optional.empty() if not found.
         * The max tx fee rate filtering should have passed prior to calling this method.
         *
         * @param offers to filter
         */
        Optional<OfferInfo> findTakeableOffer(List<OfferInfo> offers) {
            if (iHavePreferredTradingPeers.get())
                return offers.stream()
                        .filter(o -> usesSamePaymentMethod.test(o, getPaymentAccount()))
                        .filter(isMakerPreferredTradingPeer)
                        .filter(o -> isMarginGEMinMarketPriceMargin.test(o, minMarketPriceMargin)
                                || isFixedPriceGEMinMarketPriceMargin.test(o, currentMarketPrice))
                        .filter(o -> isWithinBTCAmountBounds(o, getMinAmount(), getMaxAmount()))
                        .findFirst();
            else
                return offers.stream()
                        .filter(o -> usesSamePaymentMethod.test(o, getPaymentAccount()))
                        .filter(o -> isMarginGEMinMarketPriceMargin.test(o, minMarketPriceMargin)
                                || isFixedPriceGEMinMarketPriceMargin.test(o, currentMarketPrice))
                        .filter(o -> isWithinBTCAmountBounds(o, getMinAmount(), getMaxAmount()))
                        .findFirst();
        }

        void printCriteriaSummary() {
            if (isZero.test(minMarketPriceMargin)) {
                log.info("Looking for offers to {}, priced at or higher than the current market price of {} BTC.",
                        MARKET_DESCRIPTION,
                        currentMarketPrice);
            } else {
                log.info("Looking for offers to {}, priced at or higher than {}% {} the current market price of {} BTC.",
                        MARKET_DESCRIPTION,
                        minMarketPriceMargin.abs(), // Hide the sign, text explains target price % "above or below".
                        aboveOrBelowMinMarketPriceMargin.apply(minMarketPriceMargin),
                        currentMarketPrice);
            }
        }

        void printOffersAgainstCriteria(List<OfferInfo> offers) {
            log.info("Currently available {} offers -- want to take {} offer with price >= {} BTC.",
                    MARKET_DESCRIPTION,
                    CURRENCY_CODE,
                    targetPrice);
            printOffersSummary(offers);
        }

        void printOfferAgainstCriteria(OfferInfo offer) {
            printOfferSummary(offer);

            var filterResultsByLabel = new LinkedHashMap<String, Object>();
            filterResultsByLabel.put("Current Market Price:", currentMarketPrice + " BTC");
            filterResultsByLabel.put("Target Price (Min):", targetPrice + " BTC");
            filterResultsByLabel.put("Offer Price:", offer.getPrice() + " BTC");
            filterResultsByLabel.put("Offer maker used same payment method?",
                    usesSamePaymentMethod.test(offer, getPaymentAccount()));
            filterResultsByLabel.put("Is offer maker a preferred trading peer?",
                    iHavePreferredTradingPeers.get()
                            ? isMakerPreferredTradingPeer.test(offer) ? "YES" : "NO"
                            : "N/A");

            if (offer.getUseMarketBasedPrice()) {
                var marginPriceLabel = format("Is offer's price margin (%s%%) >= bot's min market price margin (%s%%)?",
                        offer.getMarketPriceMarginPct(),
                        minMarketPriceMargin);
                filterResultsByLabel.put(marginPriceLabel, isMarginLEMaxMarketPriceMargin.test(offer, minMarketPriceMargin));
            } else {
                var fixedPriceLabel = format("Is offer's fixed-price (%s) >= bot's target price (%s)?",
                        offer.getPrice() + " BTC",
                        targetPrice + " BTC");
                filterResultsByLabel.put(fixedPriceLabel, isFixedPriceGEMinMarketPriceMargin.test(offer, currentMarketPrice));
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
