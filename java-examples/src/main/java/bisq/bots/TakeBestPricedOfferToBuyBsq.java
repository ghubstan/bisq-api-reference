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
 * This bot's general use case is to sell your BSQ for BTC at a high BTC price.  It periodically checks the
 * Buy BSQ (Sell BTC) market, and takes a configured maximum number of offers to buy BSQ from you according to criteria
 * you define in the bot's configuration file:  <b>TakeBestPricedOfferToBuyBsq.properties</b> (located in project's
 * src/main/resources directory).  You will need to replace the default values in the configuration file for your
 * use cases.
 * <p><br/>
 * After the maximum number of BSQ swap offers have been taken (good to start with 1), the bot will shut down.  The API
 * daemon will not be shut down because swaps do not require additional payment related steps taken outside Bisq, or
 * in the GUI.
 * <p>
 * Here is one possible use case:
 * <pre>
 *      Take 1 BSQ swap offer to buy BSQ with BTC, priced no lower than 0.50% above the 30-day average BSQ price if:
 *
 *          the offer's BTC amount is between 0.10 and 0.25 BTC
 *          the offer maker is one of two preferred trading peers
 *          the current transaction mining fee rate is below 20 sats / byte
 *
 *  The bot configurations for these rules are set in TakeBestPricedOfferToBuyBsq.properties as follows:
 *
 *          maxTakeOffers=1
 *          minMarketPriceMargin=0.50
 *          minAmount=0.10
 *          maxAmount=0.25
 *          preferredTradingPeers=preferred-address-1.onion:9999,preferred-address-2.onion:9999
 *          maxTxFeeRate=20
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
 *     TakeBestPricedOfferToBuyBsq  --password=api-password --port=api-port [--dryrun=true|false]
 * </pre>
 * <p>
 * The '--simulate-regtest-payment=true' option is ignored by this bot.  Taking a swap triggers execution of the swap.
 *
 * @see <a href="https://github.com/bisq-network/bisq-api-reference/blob/make-proto-downloader-runnable-from-any-dir/java-examples/src/main/java/bisq/bots/Config.java">bisq.bots.Config.java</a>
 */
@Slf4j
@Getter
public class TakeBestPricedOfferToBuyBsq extends AbstractBot {

    // Taker bot's default BSQ payment account trading currency code.
    private static final String CURRENCY_CODE = "BSQ";

    // Config file:  resources/TakeBestPricedOfferToBuyBsq.properties.
    private final Properties configFile;
    // Taker bot's default BSQ Swap payment account.
    private final PaymentAccount paymentAccount;
    // Taker bot's minimum market price margin.  A takeable BSQ Swap offer's fixed-price must be >= minMarketPriceMargin (%).
    // Note:  all BSQ Swap offers have a fixed-price, but the bot uses a margin (%) of the 30-day price for comparison.
    private final BigDecimal minMarketPriceMargin;
    // Hard coded 30-day average BSQ trade price, used for development over regtest (ignored when running on mainnet).
    private final BigDecimal regtest30DayAvgBsqPrice;
    // Taker bot's minimum BTC amount to trade.  A takeable offer's amount must be >= minAmount BTC.
    private final BigDecimal minAmount;
    // Taker bot's maximum BTC amount to trade.  A takeable offer's amount must be <= maxAmount BTC.
    private final BigDecimal maxAmount;
    // Taker bot's max acceptable transaction fee rate.
    private final long maxTxFeeRate;
    // Maximum # of offers to take during one bot session (shut down bot after taking N swap offers).
    private final int maxTakeOffers;

    // Offer polling frequency must be > 1000 ms between each getoffers request.
    private final long pollingInterval;

    // The # of offers taken during the bot session (since startup).
    private int numOffersTaken = 0;

    public TakeBestPricedOfferToBuyBsq(String[] args) {
        super(args);
        pingDaemon(new Date().getTime()); // Shut down now if API daemon is not available.
        this.configFile = loadConfigFile();
        this.paymentAccount = getBsqSwapPaymentAccount();
        this.minMarketPriceMargin = new BigDecimal(configFile.getProperty("minMarketPriceMargin"))
                .setScale(2, HALF_UP);
        this.regtest30DayAvgBsqPrice = new BigDecimal(configFile.getProperty("regtest30DayAvgBsqPrice"))
                .setScale(8, HALF_UP);
        this.minAmount = new BigDecimal(configFile.getProperty("minAmount"));
        this.maxAmount = new BigDecimal(configFile.getProperty("maxAmount"));
        this.maxTxFeeRate = Long.parseLong(configFile.getProperty("maxTxFeeRate"));
        this.maxTakeOffers = Integer.parseInt(configFile.getProperty("maxTakeOffers"));
        loadPreferredOnionAddresses.accept(configFile, preferredTradingPeers);
        this.pollingInterval = Long.parseLong(configFile.getProperty("pollingInterval"));
    }

    /**
     * Checks for the most attractive BSQ Swap offer to take every {@link #pollingInterval} ms.
     * Will only terminate when manually shut down, or a fatal gRPC StatusRuntimeException is thrown.
     */
    @Override
    public void run() {
        var startTime = new Date().getTime();
        validateWalletPassword(walletPassword);
        validatePollingInterval(pollingInterval);
        printBotConfiguration();

        while (!isShutdown) {
            if (!isBisqNetworkTxFeeRateLowEnough.test(maxTxFeeRate)) {
                runCountdown(log, pollingInterval);
                continue;
            }

            // Get all available sell BTC for BSQ offers, sorted by price descending.
            // The list contains only fixed-priced offers.
            var offers = getOffers(SELL.name(), CURRENCY_CODE).stream()
                    .filter(o -> !isAlreadyTaken.test(o))
                    .toList();

            if (offers.isEmpty()) {
                log.info("No takeable offers found.");
                runCountdown(log, pollingInterval);
                continue;
            }

            // Define criteria for taking an offer, based on conf file.
            TakeBestPricedOfferToBuyBsq.TakeCriteria takeCriteria = new TakeBestPricedOfferToBuyBsq.TakeCriteria();
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

            runCountdown(log, pollingInterval);
            pingDaemon(startTime);
        }
    }

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
                printBTCBalances("BTC Balances Before Swap Execution");
                printBSQBalances("BSQ Balances Before Swap Execution");

                // Blocks until swap is executed, or times out.
                takeBsqSwapOffer(offer, pollingInterval);

                printBTCBalances("BTC Balances After Swap Execution");
                printBSQBalances("BSQ Balances After Swap Execution");

                numOffersTaken++;
            } catch (NonFatalException nonFatalException) {
                handleNonFatalException(nonFatalException, pollingInterval);
            } catch (StatusRuntimeException fatalException) {
                handleFatalBsqSwapException(fatalException);
            }
        }
        maybeShutdownAfterSuccessfulSwap(numOffersTaken, maxTakeOffers);
    }

    private void printBotConfiguration() {
        var configsByLabel = new LinkedHashMap<String, Object>();
        configsByLabel.put("Bot OS:", getOSName() + " " + getOSVersion());
        var network = getNetwork();
        configsByLabel.put("BTC Network:", network);
        configsByLabel.put("Dry Run?", isDryRun ? "YES" : "NO");
        var isMainnet = network.equalsIgnoreCase("mainnet");
        var mainnet30DayAvgBsqPrice = isMainnet ? get30DayAvgBsqPriceInBtc() : null;
        configsByLabel.put("My Payment Account:", "");
        configsByLabel.put("\tPayment Account Id:", paymentAccount.getId());
        configsByLabel.put("\tAccount Name:", paymentAccount.getAccountName());
        configsByLabel.put("\tCurrency Code:", CURRENCY_CODE);
        configsByLabel.put("Trading Rules:", "");
        configsByLabel.put("\tMax # of offers bot can take:", maxTakeOffers);
        configsByLabel.put("\tMax Tx Fee Rate:", maxTxFeeRate + " sats/byte");
        configsByLabel.put("\tMin Market Price Margin:", minMarketPriceMargin + "%");
        if (isMainnet) {
            configsByLabel.put("\tMainnet 30-Day Avg BSQ Price:", mainnet30DayAvgBsqPrice + " BTC");
        } else {
            configsByLabel.put("\tRegtest 30-Day Avg BSQ Price:", regtest30DayAvgBsqPrice + " BTC");
        }
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

    /**
     * Return true is fixed-price offer's price >= the bot's max market price margin.  Allows bot to take a
     * fixed-priced offer if the price is >= {@link #minMarketPriceMargin} (%) of the current market price.
     */
    protected final BiPredicate<OfferInfo, BigDecimal> isFixedPriceGEMaxMarketPriceMargin =
            (offer, currentMarketPrice) -> isFixedPriceGEMinMarketPriceMargin(
                    offer,
                    currentMarketPrice,
                    this.getMinMarketPriceMargin());

    public static void main(String[] args) {
        TakeBestPricedOfferToBuyBsq bot = new TakeBestPricedOfferToBuyBsq(args);
        bot.run();
    }

    /**
     * Calculates additional takeoffer criteria based on conf file values,
     * performs candidate offer filtering, and provides useful log statements.
     */
    private class TakeCriteria {
        private static final String MARKET_DESCRIPTION = "Buy BSQ (Sell BTC)";

        private final BigDecimal avgBsqPrice;
        @Getter
        private final BigDecimal targetPrice;

        public TakeCriteria() {
            this.avgBsqPrice = isConnectedToMainnet() ? get30DayAvgBsqPriceInBtc() : regtest30DayAvgBsqPrice;
            this.targetPrice = calcTargetBsqPrice(minMarketPriceMargin, avgBsqPrice);
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
                        .filter(o -> isFixedPriceGEMaxMarketPriceMargin.test(o, avgBsqPrice))
                        .filter(o -> isWithinBTCAmountBounds(o, getMinAmount(), getMaxAmount()))
                        .findFirst();
            else
                return offers.stream()
                        .filter(o -> usesSamePaymentMethod.test(o, getPaymentAccount()))
                        .filter(o -> isFixedPriceGEMaxMarketPriceMargin.test(o, avgBsqPrice))
                        .filter(o -> isWithinBTCAmountBounds(o, getMinAmount(), getMaxAmount()))
                        .findFirst();
        }

        void printCriteriaSummary() {
            if (isZero.test(minMarketPriceMargin)) {
                log.info("Looking for offers to {}, with a fixed-price at or higher than"
                                + " the 30-day average BSQ trade price of {} BTC.",
                        MARKET_DESCRIPTION,
                        avgBsqPrice);
            } else {
                log.info("Looking for offers to {}, with a fixed-price at or higher than"
                                + " {}% {} the 30-day average BSQ trade price of {} BTC.",
                        MARKET_DESCRIPTION,
                        minMarketPriceMargin.abs(), // Hide the sign, text explains target price % "above or below".
                        aboveOrBelowMinMarketPriceMargin.apply(minMarketPriceMargin),
                        avgBsqPrice);
            }
        }

        void printOffersAgainstCriteria(List<OfferInfo> offers) {
            log.info("Currently available {} offers -- want to take BSQ swap offer with fixed-price >= {} BTC.",
                    MARKET_DESCRIPTION,
                    targetPrice);
            printOffersSummary(offers);
        }

        void printOfferAgainstCriteria(OfferInfo offer) {
            printOfferSummary(offer);

            var filterResultsByLabel = new LinkedHashMap<String, Object>();
            filterResultsByLabel.put("30-day Avg BSQ trade price:", avgBsqPrice + " BTC");
            filterResultsByLabel.put("Target Price (Min):", targetPrice + " BTC");
            filterResultsByLabel.put("Offer Price:", offer.getPrice() + " BTC");
            filterResultsByLabel.put("Offer maker used same payment method?",
                    usesSamePaymentMethod.test(offer, getPaymentAccount()));
            filterResultsByLabel.put("Is offer's maker a preferred trading peer?",
                    iHavePreferredTradingPeers.get()
                            ? isMakerPreferredTradingPeer.test(offer) ? "YES" : "NO"
                            : "N/A");
            var fixedPriceLabel = format("Is offer fixed-price (%s) >= bot's minimum price (%s)?",
                    offer.getPrice() + " BTC",
                    targetPrice + " BTC");
            filterResultsByLabel.put(fixedPriceLabel, isFixedPriceGEMaxMarketPriceMargin.test(offer, avgBsqPrice));
            var btcAmountBounds = format("%s BTC - %s BTC", minAmount, maxAmount);
            filterResultsByLabel.put("Is offer's BTC amount within bot amount bounds (" + btcAmountBounds + ")?",
                    isWithinBTCAmountBounds(offer, getMinAmount(), getMaxAmount()));

            var title = format("Fixed price BSQ swap offer %s filter results:", offer.getId());
            log.info(toTable.apply(title, filterResultsByLabel));
        }
    }
}
