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

import io.grpc.StatusRuntimeException;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import protobuf.PaymentAccount;

import java.util.Properties;

import static bisq.bots.BotUtils.*;
import static bisq.proto.grpc.GetTradesRequest.Category.CLOSED;
import static io.grpc.Status.Code.PERMISSION_DENIED;

/**
 * Simulates trade payment protocol steps on the BTC regtest network only (useful in bot development and testing).
 */
@Slf4j
@Getter
public class RegtestTradePaymentSimulator extends AbstractBot {

    // Config file:  resources/RegtestTradePaymentSimulator.properties.
    private final Properties configFile;

    // Payment simulator bot's payment account (passed from bot that created the trade).
    private final PaymentAccount paymentAccount;
    // Payment simulator bot's trade (passed from bot that created the trade).
    private final String tradeId;
    // Payment simulator bot's trade currency code (derived from payment account's selected currency code).
    private final String currencyCode;
    // Defined in RegtestTradePaymentSimulator.properties.
    private final long pollingInterval;

    // Constructor
    public RegtestTradePaymentSimulator(String[] args, String tradeId, PaymentAccount paymentAccount) {
        super(args);
        this.tradeId = tradeId;
        this.paymentAccount = paymentAccount;
        this.currencyCode = paymentAccount.getSelectedTradeCurrency().getCode();
        this.configFile = loadConfigFile();
        this.pollingInterval = Long.parseLong(configFile.getProperty("pollingInterval"));
    }

    /**
     * Performs trade protocol steps starting after a successful takeoffer request, resulting in a new trade.
     * <p>
     * If the calling bot is a BTC buyer, will send a fiat or XMR confirmpaymentstarted message to trading peer,
     * then print a CLI command for the trading peer to run and wait for a confirmpaymentreceived from the trading peer,
     * then close the trade.
     * <p>
     * If the calling bot is a BTC seller, will print a CLI confirmpaymentstarted command for the trading peer to run,
     * then wait for a confirmpaymentstarted from the trading peer.  After this bot receives the confirmpaymentstarted
     * message from the trading peer, will send a confirmpaymentreceived, then close the trade.
     * <p>
     * Never run this on mainnet.  If you attempt to run this bot on mainnet, it will throw a fatal gRPC
     * StatusRuntimeException(PERMISSION_DENIED).
     */
    @Override
    public void run() {
        verifyNotConnectedToMainnet();

        waitForTakerDepositTxConfirmation();

        var trade = getTrade(tradeId);

        // All Bisq trades are based on buying or selling BTC.  When a user thinks of "buying XMR (with BTC)",
        // Bisq's code treats it as "selling BTC (for XMR)".  This can be confusing;  try not to allow Bisq UI labels
        // and conversations about trading on Bisq mix you (API bot coder) up.
        var iAmBtcBuyer = isBtcBuyer.test(trade);
        if (iAmBtcBuyer) {
            // I (bot) am BTC buyer.  I send a confirmpaymentstarted msg and wait for a confirmpaymentreceived msg.
            sendPaymentStartedMessage();
            printCliPaymentReceivedConfirmationCommand(log,
                    "xyz",
                    9999,
                    currencyCode,
                    trade.getTradeId());
            waitForPaymentReceivedConfirmationMessage();
        } else {
            // I (bot) am BTC seller.  I wait for a confirmpaymentstarted msg and send a confirmpaymentreceived msg.
            printCliPaymentStartedCommand(log,
                    "xyz",
                    9999,
                    currencyCode,
                    trade.getTradeId());
            waitForPaymentStartedMessage();
            sendPaymentReceivedConfirmationMessage();
        }

        sleep(pollingInterval);
        closeTrade(tradeId);
        log.info("You closed the trade here in the bot (mandatory, to move trades to history list).");

        log.warn("##############################################################################");
        log.warn("Bob closes trade in the CLI (mandatory, to move trades to history list):");
        String copyPasteCliCommands = "./bisq-cli --password=xyz --port=9999 closetrade --trade-id=" + trade.getTradeId()
                + "\n" + "./bisq-cli --password=xyz --port=9999 gettrades --category=closed";
        log.warn(copyPasteCliCommands);
        log.warn("##############################################################################");

        sleep(pollingInterval);
        log.info("Trade is completed.  Here are today's completed trades:");
        printTradesSummaryForToday(CLOSED);

        log.info("Closing {}'s gRPC channel.", this.getClass().getSimpleName());
        super.grpcStubs.close();
    }

    private void waitForTakerDepositTxConfirmation() {
        var trade = getTrade(tradeId);
        while (!trade.getIsDepositConfirmed()) {
            log.info("The trade's taker deposit tx `{}` has not yet been confirmed on the bitcoin blockchain.",
                    trade.getDepositTxId());
            sleep(pollingInterval);
            trade = getTrade(trade.getTradeId());
        }
        printTradeSummary(trade);
        log.info("The trade's taker deposit tx `{}` has been confirmed on the bitcoin blockchain.",
                trade.getDepositTxId());
    }

    private void waitForPaymentStartedMessage() {
        var trade = getTrade(tradeId);
        while (!trade.getIsPaymentStartedMessageSent()) {
            log.info("The trade's {} payment has not yet been sent.", currencyCode);
            sleep(pollingInterval);
            trade = getTrade(trade.getTradeId());
        }
        printTradeSummary(trade);
        log.info("The trade's {} payment has been sent.", currencyCode);
    }

    private void sendPaymentStartedMessage() {
        log.info("You send a {} payment started message to the BTC seller.", currencyCode);
        sleep(pollingInterval);
        confirmPaymentStarted(tradeId);
        sleep(2_000);
        var trade = getTrade(tradeId);
        printTradeSummary(trade);
        log.info("You sent a {} payment started message to the BTC seller.", currencyCode);
    }

    private void waitForPaymentReceivedConfirmationMessage() {
        var trade = getTrade(tradeId);
        while (!trade.getIsPaymentReceivedMessageSent()) {
            log.info("The trade's {} payment received confirmation message has not yet been sent.", currencyCode);
            sleep(pollingInterval);
            trade = getTrade(trade.getTradeId());
        }
        printTradeSummary(trade);
        log.info("The trade's {} payment has been sent.", currencyCode);
    }

    private void sendPaymentReceivedConfirmationMessage() {
        log.info("You confirm {} payment was received to your wallet before"
                        + " sending confirmpaymentreceived to the BTC buyer.",
                currencyCode);
        sleep(pollingInterval);
        confirmPaymentReceived(tradeId);
        sleep(2_000);
        var trade = getTrade(tradeId);
        printTradeSummary(trade);
        log.info("You sent a confirmpaymentreceived message to the BTC buyer.");
    }

    private void verifyNotConnectedToMainnet() {
        if (isConnectedToMainnet()) {
            // We throw a FATAL(!) gRPC StatusRuntimeException(PERMISSION_DENIED) if the calling bot attempts
            // to simulate payment on the BTC mainnet network.  It is very unusual for a gRPC client to throw
            // a StatusRuntimeException, but make this one exception to emphasise the seriousness of the problem.
            throw new StatusRuntimeException(PERMISSION_DENIED.toStatus()
                    .withDescription("API daemon is connected to BTC mainnet!"));
        }
    }
}
