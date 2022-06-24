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

import bisq.proto.grpc.*;
import io.grpc.StatusRuntimeException;
import lombok.extern.slf4j.Slf4j;
import protobuf.PaymentAccount;

import javax.annotation.Nullable;
import java.util.Optional;

import static bisq.bots.BotUtils.*;
import static bisq.proto.grpc.GetOfferCategoryReply.OfferCategory.BSQ_SWAP;
import static io.grpc.Status.*;
import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

/**
 * This helper class exists to encapsulate takeoffer error handling, and waiting for a new trade to be fully
 * initialized trade.
 * <p>
 * For v1 protocol trades, the happy path is a successful takeoffer call, resulting in a quickly prepared, new trade.
 * But several things can go wrong.  An offer may be unavailable for one of several reasons.  When an offer availability
 * check fails while processing a takeoffer request, the server's response object will contain a "failure reason"
 * instead of a new trade, and no gRPC StatusRuntimeException is thrown.
 * <pre>
 *     See <a href="https://bisq-network.github.io/slate/#rpc-method-takeoffer">https://bisq-network.github.io/slate/#rpc-method-takeoffer</a>
 *     See <a href="https://bisq-network.github.io/slate/#availabilityresultwithdescription">https://bisq-network.github.io/slate/#availabilityresultwithdescription</a>
 *     See <a href="https://bisq-network.github.io/slate/#availabilityresult">https://bisq-network.github.io/slate/#availabilityresult</a>
 * </pre>
 * <p>
 * If offer availability checks passed, the takeoffer request can still fail for other reasons, and the API server
 * will send a gRPC StatusRuntimeException to the client.  Two likely errors are (1) insufficient BTC in the offer
 * taker's wallet, or (2) the API server's takeoffer call rate of 1 takeoffer request per minute has been exceeded.
 * In both cases, the error should not be fatal to a bot.  The bot can keep running while the user funds the wallet,
 * and the bot should stall long enough to make the rate meter happy again.
 * <p>
 * Any other gRPC StatusRuntimeException passed up to the client should be considered fatal.
 * <p>
 * The happy path for taking a BSQ Swap offer is a completed swap.
 */
@Slf4j
class OfferTaker {

    private static final int MAX_GET_NEW_TRADE_ATTEMPTS = 15;

    private final GrpcStubs grpcStubs;
    private final OfferInfo offer;
    @Nullable
    private final PaymentAccount paymentAccount;    // Not used for taking bsq swaps.
    @Nullable
    private final String bisqTradeFeeCurrency;      // Not used for taking bsq swaps.
    private final long pollingInterval;
    private final GetTradeRequest getTradeRequest;

    /**
     * Constructor for taking BSQ swap offers.
     * Payment acct id nor trade fee currency code are used in takeoffer requests.
     *
     * @param grpcStubs       gRPC service stubs, initialized with hostname, port, and credentials.
     * @param offer           The offer to take.
     * @param pollingInterval The calling bot's polling interval, in milliseconds (some situations require calculating
     *                        a stalling period before making the next request).
     */
    OfferTaker(GrpcStubs grpcStubs,
               OfferInfo offer,
               long pollingInterval) {
        this(grpcStubs, offer, null, null, pollingInterval);
    }

    /**
     * Constructor for taking v1 protocol offers (fiat or xmr).
     *
     * @param grpcStubs            gRPC service stubs,  initialized with hostname, port, and credentials.
     * @param offer                The offer to take.
     * @param paymentAccount       The payment account used to take the offer.
     * @param bisqTradeFeeCurrency The Bisq trade fee currency code (BSQ or BTC).
     * @param pollingInterval      The calling bot's polling interval, in milliseconds (some situations require
     *                             calculating a stalling period before making the next request).
     */
    OfferTaker(GrpcStubs grpcStubs,
               OfferInfo offer,
               @Nullable PaymentAccount paymentAccount,
               @Nullable String bisqTradeFeeCurrency,
               long pollingInterval) {
        this.grpcStubs = grpcStubs;
        this.offer = offer;
        this.paymentAccount = paymentAccount;
        this.bisqTradeFeeCurrency = bisqTradeFeeCurrency;
        this.pollingInterval = pollingInterval;
        this.getTradeRequest = GetTradeRequest.newBuilder().setTradeId(offer.getId()).build();
    }

    private OfferTaker() {
        throw new UnsupportedOperationException("Default, no-arg constructor is invalid.");
    }

    /**
     * Takes an offer.  Throws an exception if one of various possible causes of failure is detected.
     * Only fiat offers are supported by this class, so far.
     *
     * @throws NonFatalException      if a failure to take the offer was detected.  The offer could have been
     *                                unavailable for one of many reasons, or the taker's wallet had insufficient BTC
     *                                to cover the trade.  Other problems could result in this exception being thrown,
     *                                including attempts to make takeoffer requests more than once per minute.
     *                                None of these are fatal errors for a bot, which can opt to not terminate itself.
     * @throws StatusRuntimeException if a fatal error occurred while attempting to take the offer.
     */
    void takeOffer() throws NonFatalException {
        // What kind of offer is being taken: FIAT, ALTCOIN, or BSQ_SWAP?
        var offerCategoryRequest = GetOfferCategoryRequest.newBuilder()
                .setId(offer.getId())
                .build();
        var offerCategory = grpcStubs.offersService
                .getOfferCategory(offerCategoryRequest)
                .getOfferCategory();

        if (offerCategory.equals(BSQ_SWAP)) {
            sendTakeOfferRequest(offerCategory);
            // The happy path:  No non-fatal or fatal exception was thrown.  There was no offer availability problem,
            // no insufficient funds problem, and the takeoffer call rate meter did not block the request.  A new swap
            // is being executed on the server, and the bot should check for the new trade, then shut down.
            log.info("New BSQ swap '{}' is being executed.", offer.getId());
            // Let the daemon completely execute the swap before making any gettrade requests.
            sleep(2_000);
        } else {
            sendTakeOfferRequest(offerCategory);
            // The happy path:  No non-fatal or fatal exception was thrown.  There was no offer availability problem,
            // no insufficient funds problem, and the takeoffer call rate meter did not block the request.  A new trade
            // is being prepared on the server, and the bot should check for the new trade, then shut down so the
            // trade can be completed in the UI.
            log.info("New trade '{}' is being prepared:", offer.getId());
            printTradeSummary(getTrade());
        }
    }

    /**
     * Sends a TakeOfferRequest.  Throws a NonFatalException if there was an offer availability problem, insufficient
     * funds in the taker's wallet, or a fatal gRPC StatusRuntimeException.  If no exception is thrown, it is assumed
     * a new trade has been created and will be fully prepared in a few seconds.
     *
     * @param offerCategory FIAT | ALTCOIN | BSQ_SWAP
     * @see <a href="https://bisq-network.github.io/slate/?java#rpc-method-takeoffer">https://bisq-network.github.io/slate/?java#rpc-method-takeoffer</a>
     */
    // TODO refactor (combine) with sendTakeBsqSwapOfferRequest?
    private void sendTakeOfferRequest(GetOfferCategoryReply.OfferCategory offerCategory)
            throws NonFatalException {
        TakeOfferReply reply;
        try {
            var requestBuilder = TakeOfferRequest.newBuilder().setOfferId(offer.getId());
            if (!offerCategory.equals(BSQ_SWAP)) {
                // V1 protocol takeoffer requests require a paymentAccountId and optional takerFeeCurrencyCode.
                var paymentAccountId = requireNonNull(paymentAccount,
                        "The takeoffer requests' paymentAccountId cannot be null").getId();
                requestBuilder.setPaymentAccountId(paymentAccountId)
                        .setTakerFeeCurrencyCode(bisqTradeFeeCurrency);
            }
            // The TakeOffer reply will contain a new trade, or a reason the offer was not available for the taking.
            // However, offer availability checks do not check for sufficient funds in the taker's wallet, and that
            // situation is handled in the catch block.
            var request = requestBuilder.build();
            reply = grpcStubs.tradesService.takeOffer(request);
            if (reply.hasFailureReason()) {
                // A failure reason results from an offer availability problem, which does not include
                // an insufficient funds problem with the taker's wallet.  Neither case is fatal for the
                // bot, but the two problems must be handled differently in the client.
                // TODO Failure reason and exception due to insufficient funds needs to be treated in a more
                //  uniform manner in the server, so the client does not need to concern itself with this confusing
                //  difference.  In the UI, offer availability checks are trigger by one button click (Take Offer),
                //  and insufficient funds exceptions may be triggered by another button click (Transfer Funds
                //  For The Trade).  The API daemon needs to fix this in a a backwards compatible way.
                AvailabilityResultWithDescription reason = reply.getFailureReason();
                String errorMessage = format("Non-Fatal Error %s: %s", reason.getAvailabilityResult(), reason.getDescription());
                throw new NonFatalException(errorMessage);
            }
        } catch (StatusRuntimeException grpcException) {
            handleTakeOfferException(grpcException);
        }
    }

    /**
     * Handle a gRPC StatusRuntimeException from the API server while calling the API's takeoffer method.  Some are
     * fatal exceptions, others not.
     * <p>
     * The gRPC exception's status code will be UNAVAILABLE if there is an insufficient funds error.  This is not a
     * fatal error, and the user can fund the wallet while the bot is running.
     * <p>
     * The gRPC exception's status code will be PERMISSION_DENIED when the takeoffer request frequency is > 1 / minute.
     * This is not a fatal error.  In this case, set the NonFatalException's stallTime, so the bot can wait the minimum
     * amount of time required to avoid another StatusRuntimeException(PERMISSION_DENIED).
     * <p>
     * For any other gRPC exception status code, assumes a fatal error and throws the exception.
     */
    private void handleTakeOfferException(StatusRuntimeException ex) throws NonFatalException {
        if (exceptionHasStatus.test(ex, UNAVAILABLE)) {
            throw new NonFatalException(toNonFatalErrorMessage.apply(ex));
        } else if (exceptionHasStatus.test(ex, PERMISSION_DENIED)) {
            // Calculate how long we have to stall the bot before it can send the next takeoffer request.
            long stallTime = 60_005 - pollingInterval;
            throw new NonFatalException(toNonFatalErrorMessage.apply(ex), stallTime);
        } else {
            throw ex;
        }
    }

    /**
     * Wait and block until a new BSQ swap is executed.
     * <p>
     * Should be called immediately after a takeoffer call.  If the executed trade is not found
     * within a maximum allowed amount of time ({@link #MAX_GET_NEW_TRADE_ATTEMPTS}  * 1 second),
     * throw a fatal StatusRuntimeException(NOT_FOUND).
     */
    void waitForBsqSwapCompletion() {
        Optional<TradeInfo> newTrade = getPreparedTrade();
        if (newTrade.isPresent()) {
            TradeInfo trade = newTrade.get();
            log.info("BSQ Swap is complete:");
            printTradeSummary(trade);
        } else {
            throw new StatusRuntimeException(NOT_FOUND
                    .withDescription("Something bad happened, could not find the new trade."
                            + "    Shut down the API bot and server, then check the server log."));
        }
    }
    
    /**
     * Wait and block until a new trade is fully initialized, with a trade contract and the user's trade role.
     * <p>
     * Should be called immediately after a takeoffer call.  If the new trade is not initialized within a maximum
     * amount of time ({@link #MAX_GET_NEW_TRADE_ATTEMPTS}  * 1 second) throw a fatal
     * StatusRuntimeException(NOT_FOUND).
     */
    void waitForTradePreparation() {
        Optional<TradeInfo> newTrade = getPreparedTrade();
        if (newTrade.isPresent()) {
            TradeInfo trade = newTrade.get();
            log.info("New trade has been prepared:");
            printTradeSummary(trade);
        } else {
            throw new StatusRuntimeException(NOT_FOUND
                    .withDescription("Something bad happened, could not find the new trade."
                            + "    Shut down the API bot and server, then check the server log."));
        }
    }

    /**
     * Calls {@link #getNewTrade} every second, for a maximum of {@link #MAX_GET_NEW_TRADE_ATTEMPTS} seconds, or
     * until the newly prepared trade is found -- whichever comes first.
     * <p>
     * If the newly prepared trade is found within the time limit, returns an  Optional<TradeInfo> object, else
     * throws a gRPC StatusRuntimeException with Status.Code = NOT_FOUND.
     *
     * @return Optional<TradeInfo> containing a prepared trade.
     */
    private Optional<TradeInfo> getPreparedTrade() {
        int attempts = 0;
        while (attempts++ < MAX_GET_NEW_TRADE_ATTEMPTS - 1) {
            Optional<TradeInfo> trade = getNewTrade();
            if (trade.isPresent() && !trade.get().getRole().equalsIgnoreCase("Not Available")) {
                return trade;
            } else {
                sleep(1_000);
            }
        }
        // Try again, one last time, and throw the NOT_FOUND found exception from the gRPC server.
        return Optional.of(getTrade());
    }

    /**
     * Returns an Optional<TradeInfo> containing a trade, Optional.empty() if not found, or throws a
     * gRPC StatusRuntimeException.
     * <p>
     * Use this method if you took the trade just now and want an Optional.empty() if not found, instead of a gRPC
     * StatusRuntimeException(NOT_FOUND) exception.  Any gRPC StatusRuntimeException with a Status code != NOT_FOUND
     * will be thrown.
     *
     * @return Optional<TradeInfo> containing a trade, or Optional.empty() if not found, or throws a
     * gRPC StatusRuntimeException.
     */
    private Optional<TradeInfo> getNewTrade() {
        try {
            var trade = getTrade();
            return Optional.of(trade);
        } catch (Throwable t) {
            if (t instanceof StatusRuntimeException) {
                if (exceptionHasStatus.test((StatusRuntimeException) t, NOT_FOUND))
                    return Optional.empty();
                else
                    throw t;
            } else {
                throw t;
            }
        }
    }

    /**
     * Returns a trade, or throws a gRPC StatusRuntimeException.
     *
     * @return bisq.proto.grpc.TradeInfo
     * @see <a href="https://bisq-network.github.io/slate/?java#rpc-method-gettrade">https://bisq-network.github.io/slate/?java#rpc-method-gettrade</a>
     */
    private TradeInfo getTrade() {
        var response = grpcStubs.tradesService.getTrade(getTradeRequest);
        return response.getTrade();
    }
}
