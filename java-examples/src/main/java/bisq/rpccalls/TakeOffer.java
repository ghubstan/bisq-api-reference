package bisq.rpccalls;

import bisq.proto.grpc.GetOfferCategoryRequest;
import bisq.proto.grpc.OffersGrpc;
import bisq.proto.grpc.TakeOfferRequest;
import bisq.proto.grpc.TradesGrpc;
import io.grpc.ManagedChannelBuilder;

import static bisq.proto.grpc.GetOfferCategoryReply.OfferCategory.BSQ_SWAP;
import static java.lang.System.err;
import static java.lang.System.out;

public class TakeOffer extends BaseJavaExample {

    public static void main(String[] args) {
        try {
            var channel = ManagedChannelBuilder.forAddress("localhost", 9998).usePlaintext().build();
            addChannelShutdownHook(channel);
            var credentials = buildCallCredentials(getApiPassword());
            var offersStub = OffersGrpc.newBlockingStub(channel).withCallCredentials(credentials);
            var tradesStub = TradesGrpc.newBlockingStub(channel).withCallCredentials(credentials);

            // We need to send our payment account id, and the taker fee currency code if offer is
            // not a BSQ swap offer.  Find out by calling GetOfferCategory before taking the offer.
            var getOfferCategoryRequest = GetOfferCategoryRequest.newBuilder()
                    .setId("83e8b2e2-51b6-4f39-a748-3ebd29c22aea")
                    .build();
            var offerCategory = offersStub.getOfferCategory(getOfferCategoryRequest);
            // Create a takeoffer request builder with just the offerId parameter.
            var takeOfferRequestBuilder = TakeOfferRequest.newBuilder()
                    .setOfferId("83e8b2e2-51b6-4f39-a748-3ebd29c22aea");
            //  If offer is not a BSQ swap offer, add the paymentAccountId and takerFeeCurrencyCode parameters.
            if (!offerCategory.equals(BSQ_SWAP))
                takeOfferRequestBuilder
                        .setPaymentAccountId("f3c1ec8b-9761-458d-b13d-9039c6892413")
                        .setTakerFeeCurrencyCode("BSQ");

            var takeOfferRequest = takeOfferRequestBuilder.build();
            var takeOfferResponse = tradesStub.takeOffer(takeOfferRequest);
            if (takeOfferResponse.hasFailureReason())
                err.println("Take offer failure reason: " + takeOfferResponse.getFailureReason());
            else
                out.println("New trade: " + takeOfferResponse.getTrade());
        } catch (Throwable t) {
            handleError(t);
        }
    }
}
