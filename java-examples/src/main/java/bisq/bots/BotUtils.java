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
import bisq.proto.grpc.GetTradesRequest;
import bisq.proto.grpc.OfferInfo;
import bisq.proto.grpc.TradeInfo;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import protobuf.PaymentAccount;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Duration;
import java.util.*;
import java.util.function.*;

import static bisq.bots.CurrencyFormat.toSatoshis;
import static bisq.bots.table.builder.TableType.*;
import static java.lang.String.format;
import static java.lang.System.*;
import static java.math.BigDecimal.ZERO;
import static java.math.RoundingMode.HALF_UP;
import static java.util.Objects.requireNonNull;

/**
 * Convenience methods and functions not depending on a bot's state nor the need to send requests to the API daemon.
 */
@Slf4j
public class BotUtils {

    private static final String BANNER = "##############################################################################";

    public static final Predicate<String> isBsq = (currencyCode) -> currencyCode.equalsIgnoreCase("BSQ");
    public static final Predicate<String> isXmr = (currencyCode) -> currencyCode.equalsIgnoreCase("XMR");
    public static final Predicate<String> isAltcoin = (currencyCode) -> isBsq.test(currencyCode) || isXmr.test(currencyCode);

    /**
     * Return a timestamp for midnight, today.
     */
    public static final Supplier<Long> midnightToday = () -> {
        Calendar c = new GregorianCalendar();
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        return c.getTimeInMillis();
    };

    /**
     * Return price precision of 8 for altcoin, 4 for fiat.
     */
    public static final Function<String, Integer> toPricePrecision = (currencyCode) ->
            isAltcoin.test(currencyCode) ? 8 : 4;

    /**
     * Calculates a target price from given max market price margin and market price.
     *
     * @param targetMarketPriceMargin the maximum or minimum market price margin
     * @param currentMarketPrice      the current market price
     * @param currencyCode            the asset currency code (calculated price precision = 4 for fiat, 8 for altcoin)
     * @return BigDecimal a target price
     */
    public static BigDecimal calcTargetPrice(BigDecimal targetMarketPriceMargin,
                                             BigDecimal currentMarketPrice,
                                             String currencyCode) {
        if (!isZero.test(targetMarketPriceMargin) && targetMarketPriceMargin.precision() < 2)
            throw new IllegalArgumentException(
                    format("Price margin percent literal argument %s is invalid;"
                                    + "  it must have a precision of at least 2 decimal places.",
                            targetMarketPriceMargin));

        var maxMarketPriceMarginAsDecimal = scaleAsDecimal.apply(targetMarketPriceMargin);
        var precision = toPricePrecision.apply(currencyCode);
        return currentMarketPrice.add(currentMarketPrice
                        .multiply(maxMarketPriceMarginAsDecimal, new MathContext(precision, HALF_UP)))
                .setScale(precision, HALF_UP);
    }

    /**
     * Calculates a target BSQ price from given max market price margin and an average BSQ market price.
     *
     * @param targetMarketPriceMargin the maximum or minimum market price margin
     * @param avgBsqPrice             the average BSQ price
     * @return BigDecimal a target price
     */
    public static BigDecimal calcTargetBsqPrice(BigDecimal targetMarketPriceMargin,
                                                BigDecimal avgBsqPrice) {
        if (!isZero.test(targetMarketPriceMargin) && targetMarketPriceMargin.precision() <= 2)
            throw new IllegalArgumentException(
                    format("Price margin percent literal argument %s is invalid;"
                                    + "  it must have a precision of at least 2 decimal places.",
                            targetMarketPriceMargin));

        var maxMarketPriceMarginAsDecimal = scaleAsDecimal.apply(targetMarketPriceMargin);
        return avgBsqPrice.add(avgBsqPrice
                        .multiply(maxMarketPriceMarginAsDecimal, new MathContext(8, HALF_UP)))
                .setScale(8, HALF_UP);
    }

    /**
     * Convert milliseconds to seconds.
     */
    public static final Function<Long, Long> toSeconds = (ms) -> Duration.ofMillis(ms).getSeconds();

    /**
     * Return true if given BigDecimal equals 0.00.
     */
    public static final Predicate<BigDecimal> isZero = (d) -> d.compareTo(ZERO) == 0;

    /**
     * Convert a BigDecimal representing a % literal to a BigDecimal representing
     * the equivalent decimal, e.g., 1.00 (%) converts to 0.01.
     */
    public static final Function<BigDecimal, BigDecimal> scaleAsDecimal = (pctLiteral) ->
            pctLiteral.divide(new BigDecimal("100"), HALF_UP);

    /**
     * Return a BigDecimal representing the difference as a percentage between a base number and n,
     * i.e., how much above or below (as a %) is n compared to base?
     */
    public static final BiFunction<BigDecimal, BigDecimal, BigDecimal> diffAsPercent = (base, n) -> {
        BigDecimal factor = new BigDecimal("100");
        BigDecimal diff = n.divide(base, 4, HALF_UP).multiply(factor);
        return diff.subtract(factor);
    };


    /**
     * Return true if the offer's margin based price >= target price.
     */
    public static final BiPredicate<OfferInfo, BigDecimal> isMarginBasedPriceGETargetPrice =
            (offer, targetPrice) -> offer.getUseMarketBasedPrice()
                    && new BigDecimal(offer.getPrice()).compareTo(targetPrice) >= 0;

    /**
     * Return true if the margin price based offer's market price margin (%) >= minxMarketPriceMargin (%).
     */
    public static final BiPredicate<OfferInfo, BigDecimal> isMarginGEMinMarketPriceMargin =
            (offer, minMarketPriceMargin) -> offer.getUseMarketBasedPrice()
                    && offer.getMarketPriceMarginPct() >= minMarketPriceMargin.doubleValue();

    /**
     * Return true if the margin price based offer's market price margin (%) <= maxMarketPriceMargin (%).
     */
    public static final BiPredicate<OfferInfo, BigDecimal> isMarginLEMaxMarketPriceMargin =
            (offer, maxMarketPriceMargin) -> offer.getUseMarketBasedPrice()
                    && offer.getMarketPriceMarginPct() <= maxMarketPriceMargin.doubleValue();

    /**
     * Return true is fixed-price offer's price <= the bot's max market price margin.  Allows bot to
     * take a fixed-priced offer if the price is <= maxMarketPriceMargin (%) of the current market price.
     */
    public static boolean isFixedPriceLEMaxMarketPriceMargin(OfferInfo offer,
                                                             BigDecimal currentMarketPrice,
                                                             BigDecimal maxMarketPriceMargin) {
        if (offer.getUseMarketBasedPrice())
            return false;

        BigDecimal offerPrice = new BigDecimal(offer.getPrice());

        // How much above or below currentMarketPrice (as a %) is the offer's fixed-price?
        BigDecimal distanceFromMarketPrice = diffAsPercent.apply(currentMarketPrice, offerPrice);

        // Return true if distanceFromMarketPrice <= maxMarketPriceMargin.
        return distanceFromMarketPrice.compareTo(maxMarketPriceMargin) <= 0;
    }

    /**
     * Return true is fixed-price offer's price >= the bot's minimum market price margin.  Allows bot to
     * take a fixed-priced offer if the price is >= minMarketPriceMargin (%) of the current market price.
     */
    public static boolean isFixedPriceGEMinMarketPriceMargin(OfferInfo offer,
                                                             BigDecimal currentMarketPrice,
                                                             BigDecimal minMarketPriceMargin) {
        if (offer.getUseMarketBasedPrice())
            return false;

        BigDecimal offerPrice = new BigDecimal(offer.getPrice());

        // How much above or below currentMarketPrice (as a %) is the offer's fixed-price?
        BigDecimal distanceFromMarketPrice = diffAsPercent.apply(currentMarketPrice, offerPrice);

        // Return true if distanceFromMarketPrice <= maxMarketPriceMargin.
        return distanceFromMarketPrice.compareTo(minMarketPriceMargin) >= 0;
    }

    /**
     * Return String "above" if minMarketPriceMargin (%) >= 0.00, else "below".
     */
    public static final Function<BigDecimal, String> aboveOrBelowMinMarketPriceMargin = (minMarketPriceMargin) ->
            minMarketPriceMargin.compareTo(ZERO) >= 0 ? "above" : "below";

    /**
     * Return String "below" if maxMarketPriceMargin (%) <= 0.00, else "above".
     */
    public static final Function<BigDecimal, String> aboveOrBelowMaxMarketPriceMargin = (maxMarketPriceMargin) ->
            maxMarketPriceMargin.compareTo(ZERO) <= 0 ? "below" : "above";

    /**
     * Return true if offer.amt >= minAmount AND offer.amt <= maxAmount (within the boundaries).
     *  TODO API's takeoffer needs to support taking offer's minAmount.
     */
    public static boolean isWithinBTCAmountBounds(OfferInfo offer, BigDecimal minAmount, BigDecimal maxAmount) {
        return offer.getAmount() >= toSatoshis(minAmount) && offer.getAmount() <= toSatoshis(maxAmount);
    }

    /**
     * Return true if the given StatusRuntimeException's Status matches the given Status.
     */
    public static final BiPredicate<StatusRuntimeException, Status> exceptionHasStatus = (ex, status) ->
            ex.getStatus().getCode() == status.getCode();

    /**
     * Return a StatusRuntimeException message stripped of it's leading Status Code's enum name.
     */
    public static final Function<StatusRuntimeException, String> toCleanErrorMessage = (grpcException) ->
            grpcException.getMessage().replaceFirst("^[A-Z_]+: ", "");

    /**
     * Return a StatusRuntimeException message stripped of it's leading Status Code's enum name,
     * then prepended with String "Non-Fatal Error (", and appended with String ")".
     */
    public static final Function<StatusRuntimeException, String> toNonFatalErrorMessage = (grpcException) ->
            "Non-Fatal Error (" + toCleanErrorMessage.apply(grpcException) + ")";

    /**
     * Return true if the given offer's payment method matches the given payment account's payment method.
     */
    public static final BiPredicate<OfferInfo, PaymentAccount> usesSamePaymentMethod = (o, p) ->
            o.getPaymentMethodId().equals(p.getPaymentMethod().getId());

    /**
     * Return true if I am the BTC buyer for the given trade.
     */
    public static final Predicate<TradeInfo> isBtcBuyer = (trade) -> {
        var isMyOffer = trade.getOffer().getIsMyOffer();
        var isBuyerMakerAndSellerTaker = trade.getContract().getIsBuyerMakerAndSellerTaker();
        return isMyOffer == isBuyerMakerAndSellerTaker;
    };

    /**
     * Put the current thread to sleep for given number of milliseconds.
     */
    public static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ex) {
            // ignored
        }
    }

    /**
     * Reads a wallet password from the console, and appends it to the given program args
     * array as an additional config option, e.g.,  --wallet-password="be careful".
     * The returned String[] is the original args array, plus the wallet-password option.
     */
    public static final Function<String[], String[]> toArgsWithWalletPassword = (args) -> {
        var walletPasswordPrompt = "An encrypted wallet must be unlocked"
                + " for requests that read or update your wallet.\n"
                + "Please enter your wallet password:";
        var unvalidatedWalletPassword = readWalletPassword(walletPasswordPrompt);
        return appendWalletPasswordOpt(args, unvalidatedWalletPassword);
    };

    /**
     * Return true if the '--wallet-password' option label if found in the given program args array.
     */
    public static final Predicate<String[]> hasWalletPasswordOpt = (args) ->
            Arrays.stream(args).anyMatch(a -> a.contains("--wallet-password"));

    /**
     * Return a wallet password read from stdin.  If read from a command terminal, input will not be echoed.
     * If run in a virtual terminal (IDE console), the input will be echoed.
     *
     * @param prompt password prompt
     * @return String the password
     */
    public static String readWalletPassword(String prompt) {
        String walletPassword;
        var console = console();
        //  System.console() returns null if you do not launch your java application with a real console.
        if (console == null) {
            // Have to read it in a less secure way in the IDE's virtual console.
            out.println(prompt);
            BufferedReader reader = new BufferedReader(new InputStreamReader(in));
            try {
                walletPassword = reader.readLine();
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        } else {
            char[] pwdChars = console.readPassword(prompt);
            walletPassword = new String(pwdChars);
        }
        return walletPassword;
    }

    /**
     * Return the given String[] args with an additional --wallet-password="be careful" option appended to it.
     *
     * @param args           program arguments
     * @param walletPassword wallet password
     * @return String[] appended program arguments
     */
    public static String[] appendWalletPasswordOpt(String[] args, String walletPassword) {
        String[] walletPasswordOpt = new String[]{"--wallet-password=" + walletPassword};
        String[] newOpts = new String[args.length + 1];
        arraycopy(args, 0, newOpts, 0, args.length);
        arraycopy(walletPasswordOpt, 0, newOpts, args.length, walletPasswordOpt.length);
        return newOpts;
    }

    /**
     * Returns a validated address:port specification as a String.
     *
     * @param addressString The address:port pair being validated.
     * @return String
     */
    public static String getValidatedPeerAddress(String addressString) {
        String[] hostnameAndPort = addressString.split(":");
        String hostname;
        int port;
        try {
            if (hostnameAndPort.length < 2) {
                throw new IllegalStateException(format("Invalid preferredTradingPeers configuration:%n"
                                + "\t\t%s%n\tEach address much include a port, i.e, host:port.",
                        addressString));
            }
            hostname = hostnameAndPort[0].trim();
            port = Integer.parseInt(hostnameAndPort[1].trim());
        } catch (Exception ex) {
            throw new IllegalStateException(format("Invalid preferredTradingPeers configuration:%n"
                            + "\t\t%s%n\tMultiple addresses must be separated by commas.",
                    addressString),
                    ex);
        }
        return hostname + ":" + port;
    }

    /**
     * Return given Map transformed into a String representing a table with two columns:  label and value.
     * <p>
     * The map argument should contain only scalar values or short strings as values
     * (not lists or maps), or you will get ugly results.
     */
    public static final BiFunction<String, Map<String, Object>, String> toTable = (title, map) -> {
        var mapElements = map.entrySet();
        Supplier<Integer> longestLabel = () -> {
            int[] len = {0};  // Make implicitly final to modify in map element iteration.
            mapElements.forEach((e) -> {
                var labelLen = e.getKey().length();
                len[0] = Math.max(labelLen, len[0]);
            });
            return len[0];
        };
        int labelWidth = longestLabel.get() + 2;
        Supplier<String> resultsTable = () -> {
            var numRows = mapElements.size();
            var rows = new StringBuilder();
            int[] rowNum = {0};  // Make implicitly final to modify in map element iteration.
            mapElements.forEach((e) -> {
                var label = e.getKey();
                String value;
                if (e.getValue() instanceof Boolean) {
                    value = ((Boolean) e.getValue()) ? "YES" : "NO";
                } else {
                    value = e.getValue().toString();
                }
                var rowFormatSpec = (label.startsWith("\t"))
                        ? "%-" + labelWidth + "s" + " " + "%s"
                        : "%-" + (labelWidth + 3) + "s" + " " + "%s";
                var row = format(rowFormatSpec, label, value);
                rows.append("\t").append(row);
                if (++rowNum[0] < numRows) {
                    rows.append("\n");
                }
            });
            return rows.toString();
        };
        return title + "\n" +
                resultsTable.get();
    };

    /**
     * Print offer summary to stdout.
     *
     * @param offer printed offer
     */
    public static void printOfferSummary(OfferInfo offer) {
        requireNonNull(offer, "OfferInfo offer param cannot be null.");
        new TableBuilder(OFFER_TBL, offer).build().print(out);
    }

    /**
     * Print list of offer summaries to stdout
     *
     * @param offers printed offer list
     */
    public static void printOffersSummary(List<OfferInfo> offers) {
        requireNonNull(offers, "List<OfferInfo> offers param cannot be null.");
        if (offers.isEmpty()) {
            log.info("No offers to print.");
        } else {
            new TableBuilder(OFFER_TBL, offers).build().print(out);
        }
    }

    /**
     * Print trade summary to stdout.
     *
     * @param trade printed trade
     */
    public static void printTradeSummary(TradeInfo trade) {
        requireNonNull(trade, "TradeInfo trade param cannot be null.");
        new TableBuilder(TRADE_DETAIL_TBL, trade).build().print(out);
    }

    /**
     * Print list of trade summaries to stdout.
     *
     * @param category category OPEN | CLOSED | FAILED
     * @param trades   list of trades
     */
    public static void printTradesSummary(GetTradesRequest.Category category, List<TradeInfo> trades) {
        requireNonNull(trades, "List<TradeInfo> trades param cannot be null.");
        if (trades.isEmpty()) {
            log.info("No trades to print.");
        } else {
            switch (category) {
                case CLOSED -> new TableBuilder(CLOSED_TRADES_TBL, trades).build().print(out);
                case FAILED -> new TableBuilder(FAILED_TRADES_TBL, trades).build().print(out);
                default -> new TableBuilder(OPEN_TRADES_TBL, trades).build().print(out);
            }
        }
    }

    /**
     * Prints PaymentAccount summary to stdout.
     *
     * @param paymentAccount the printed PaymentAccount
     */
    public static void printPaymentAccountSummary(PaymentAccount paymentAccount) {
        requireNonNull(paymentAccount, "PaymentAccount paymentAccount param cannot be null.");
        new TableBuilder(PAYMENT_ACCOUNT_TBL, paymentAccount).build().print(out);
    }

    /**
     * Log a CLI confirmpaymentstarted command for a simulated trading peer.
     *
     * @param log                    calling bot's logger
     * @param tradingPeerApiPassword trading peer's CLI --password param value
     * @param tradingPeerApiPort     trading peer's CLI --port param value
     * @param tradeId                trade's unique identifier (cannot be short-id)
     */
    public static void printCliPaymentStartedCommand(Logger log,
                                                     String tradingPeerApiPassword,
                                                     int tradingPeerApiPort,
                                                     String currencyCode,
                                                     String tradeId) {
        log.warn(BANNER);
        log.warn("BTC buyer must manually confirm {} payment has been sent"
                        + " with a confirmpaymentstarted CLI command:",
                currencyCode);
        log.warn("./bisq-cli --password={} --port={} confirmpaymentstarted --trade-id={}",
                tradingPeerApiPassword,
                tradingPeerApiPort,
                tradeId);
        log.warn(BANNER);
    }

    /**
     * Log a CLI confirmpaymentreceived command for a simulated trading peer.
     *
     * @param log                    calling bot's logger
     * @param tradingPeerApiPassword trading peer's CLI --password param value
     * @param tradingPeerApiPort     trading peer's CLI --port param value
     * @param tradeId                trade's unique identifier (cannot be short-id)
     */
    public static void printCliPaymentReceivedConfirmationCommand(Logger log,
                                                                  String tradingPeerApiPassword,
                                                                  int tradingPeerApiPort,
                                                                  String currencyCode,
                                                                  String tradeId) {
        log.warn(BANNER);
        log.warn("BTC seller must manually confirm {} payment was received"
                        + " with a confirmpaymentreceived CLI command:",
                currencyCode);
        log.warn("./bisq-cli --password={} --port={} confirmpaymentreceived --trade-id={}",
                tradingPeerApiPassword,
                tradingPeerApiPort,
                tradeId);
        log.warn(BANNER);
    }

    /**
     * Log a CLI closetrade command for a simulated trading peer.
     *
     * @param log                    calling bot's logger
     * @param tradingPeerApiPassword trading peer's CLI --password param value
     * @param tradingPeerApiPort     trading peer's CLI --port param value
     * @param tradeId                trade's unique identifier (cannot be short-id)
     */
    public static void printCliCloseTradeCommand(Logger log,
                                                 String tradingPeerApiPassword,
                                                 int tradingPeerApiPort,
                                                 String tradeId) {
        log.warn(BANNER);
        log.warn("Trading peer must manually close trade with a closetrade CLI command:");
        log.warn("./bisq-cli --password={} --port={} closetrade --trade-id={}",
                tradingPeerApiPassword,
                tradingPeerApiPort,
                tradeId);
        log.warn(BANNER);
    }

    /**
     * Log a CLI gettrades --category=closed command for a simulated trading peer.
     *
     * @param log                    calling bot's logger
     * @param tradingPeerApiPassword trading peer's CLI --password param value
     * @param tradingPeerApiPort     trading peer's CLI --port param value
     */
    public static void printCliGetClosedTradesCommand(Logger log,
                                                      String tradingPeerApiPassword,
                                                      int tradingPeerApiPort) {
        log.warn(BANNER);
        log.warn("Trading peer can view completed trade history with a gettrades CLI command:");
        log.warn("./bisq-cli --password={} --port={} gettrades --category=closed",
                tradingPeerApiPassword,
                tradingPeerApiPort);
        log.warn(BANNER);
    }

    /**
     * Run a bash script to count down the given number of seconds, printing each character of output from stdout.
     * <p>
     * Can only be run if the system's bash command language interpreter can be found.
     *
     * @param seconds to count down
     */
    public static void showCountdown(int seconds) {
        getBashPath().ifPresentOrElse((bashPath) -> {
            var bashScript = format(
                    "for i in {%d..1}; do echo -ne \"Waking up in $i seconds...\\r\" && sleep 1; done; echo -ne \"\\r\"", seconds);
            try {
                BotUtils.runBashCommand(bashScript);
            } catch (IOException ex) {
                throw new RuntimeException("Error running bash script.", ex);
            } catch (InterruptedException ignored) {
                // ignored
            }
        }, () -> {
            throw new UnsupportedOperationException("Bash command language interpreter not found.");
        });
    }

    /**
     * Execute a bash system command, print process' stdout during the command's execution,
     * and return its status code (0 or 1).
     *
     * @param bashCommand the system bash command
     * @return int system command status code
     * @throws IOException                   if an I/O error occurs
     * @throws InterruptedException          if the current thread is interrupted by another thread while it is waiting,
     *                                       then the wait is ended and an InterruptedException is thrown.
     * @throws UnsupportedOperationException if the command language interpreter could not be found on the system, or
     *                                       if the operating system does not support the creation of processes.
     */
    @SuppressWarnings("UnusedReturnValue")
    public static int runBashCommand(String bashCommand) throws IOException, InterruptedException {
        var bashPath = getBashPath();
        if (bashPath.isPresent()) {
            List<String> cmdOptions = new ArrayList<>() {{
                //noinspection OptionalGetWithoutIsPresent
                add(bashPath.get());
                add("-c");
                add(bashCommand);
            }};
            Process process = new ProcessBuilder(cmdOptions).start();
            try (InputStreamReader isr = new InputStreamReader(process.getInputStream())) {
                int c;
                while ((c = isr.read()) >= 0) {
                    out.print((char) c);
                    out.flush();
                }
            }
            return process.waitFor();
        } else {
            throw new UnsupportedOperationException("Bash util not found on this " + getOSName() + " system.");
        }
    }

    /**
     * Return an Optional<String> for the absolute path of the system's bash utility,
     * if it exists at one of two locations:  "/bin/bash", or "/usr/bin/bash".
     *
     * @return Optional<String>
     */
    public static Optional<String> getBashPath() {
        if (isUnix()) {
            var f1 = new File("/bin/bash");
            var f2 = new File("/usr/bin/bash");
            if (f1.exists() && f1.canExecute()) {
                return Optional.of(f1.getAbsolutePath());
            } else if (f2.exists() && f2.canExecute()) {
                return Optional.of(f2.getAbsolutePath());
            } else {
                return Optional.empty();
            }
        } else {
            return Optional.empty();
        }
    }

    /**
     * Return true if OS is any flavor of Linux.
     *
     * @return true if OS is any flavor of Linux
     */
    public static boolean isUnix() {
        return isOSX() || isLinux() || getOSName().contains("freebsd");
    }

    /**
     * Return true if OS is Windows.
     *
     * @return true if OS is Windows
     */
    public static boolean isWindows() {
        return getOSName().contains("win");
    }

    /**
     * Return true if running on a virtualized OS within Qubes.
     *
     * @return true if running on a virtualized OS within Qubes
     */
    public static boolean isQubesOS() {
        // For Linux qubes, "os.version" looks like "4.19.132-1.pvops.qubes.x86_64"
        // The presence of the "qubes" substring indicates this Linux is running as a qube
        // This is the case for all 3 virtualization modes (PV, PVH, HVM)
        // In addition, this works for both simple AppVMs, as well as for StandaloneVMs
        // TODO This might not work for detecting Qubes virtualization for other OSes
        // like Windows
        return getOSVersion().contains("qubes");
    }

    /**
     * Return true if OS is Mac.
     *
     * @return true if OS is Mac
     */
    public static boolean isOSX() {
        return getOSName().contains("mac") || getOSName().contains("darwin");
    }

    /**
     * Return true if OS is Linux.
     *
     * @return true if OS is Linux
     */
    public static boolean isLinux() {
        return getOSName().contains("linux");
    }

    /**
     * Return true if OS is Debian Linux.
     *
     * @return true if OS is Debian Linux
     */
    public static boolean isDebianLinux() {
        return isLinux() && new File("/etc/debian_version").isFile();
    }

    /**
     * Return true if OS is Redhat Linux.
     *
     * @return true if OS is Redhat Linux
     */
    public static boolean isRedHatLinux() {
        return isLinux() && new File("/etc/redhat-release").isFile();
    }

    /**
     * Returns the OS name in lower case.
     *
     * @return OS name
     */
    public static String getOSName() {
        return System.getProperty("os.name").toLowerCase(Locale.US);
    }

    /**
     * Returns the OS version in lower case.
     *
     * @return OS version
     */
    public static String getOSVersion() {
        return System.getProperty("os.version").toLowerCase(Locale.US);
    }
}
