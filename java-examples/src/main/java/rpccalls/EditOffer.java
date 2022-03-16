package rpccalls;

import bisq.proto.grpc.EditOfferRequest;
import bisq.proto.grpc.OffersGrpc;
import io.grpc.ManagedChannelBuilder;

import static bisq.proto.grpc.EditOfferRequest.EditType.*;
import static bisq.proto.grpc.EditOfferRequest.newBuilder;
import static java.lang.System.out;

public class EditOffer extends BaseJavaExample {

    public static void main(String[] args) {
        try {
            var channel = ManagedChannelBuilder.forAddress("localhost", 9998).usePlaintext().build();
            addChannelShutdownHook(channel);
            var credentials = buildCallCredentials(getApiPassword());
            var stub = OffersGrpc.newBlockingStub(channel).withCallCredentials(credentials);

            var disableOfferRequest = createDisableOfferRequest();
            stub.editOffer(disableOfferRequest);
            out.println("Offer is disabled (removed from peers' offer book).");
            waitForOfferToBeRepublished(2_500);

            var editFixedPriceRequest = createEditFixedPriceRequest();
            stub.editOffer(editFixedPriceRequest);
            out.println("Offer has been re-published with new fixed-price.");
            waitForOfferToBeRepublished(2_500);

            var editFixedPriceAndEnableRequest = createEditFixedPriceAndEnableRequest();
            stub.editOffer(editFixedPriceAndEnableRequest);
            out.println("Offer has been re-published with new fixed-price, and enabled.");
            waitForOfferToBeRepublished(2_500);

            var editPriceMarginRequest = createEditPriceMarginRequest();
            stub.editOffer(editPriceMarginRequest);
            out.println("Offer has been re-published with new price margin.");
            waitForOfferToBeRepublished(2_500);

            var editTriggerPriceRequest = createEditTriggerPriceRequest();
            stub.editOffer(editTriggerPriceRequest);
            out.println("Offer has been re-published with new trigger price.");
        } catch (Throwable t) {
            handleError(t);
        }
    }

    private static EditOfferRequest createDisableOfferRequest() {
        return newBuilder()
                .setId("83e8b2e2-51b6-4f39-a748-3ebd29c22aea")
                .setEditType(ACTIVATION_STATE_ONLY)
                .setEnable(0)   // -1 = ignore this parameter, 0 = disable offer, 1 = enable offer
                .build();
    }

    private static EditOfferRequest createEditFixedPriceRequest() {
        return newBuilder()
                .setId("83e8b2e2-51b6-4f39-a748-3ebd29c22aea")
                .setEditType(FIXED_PRICE_ONLY)
                .setPrice("30000.99")
                .build();
    }

    private static EditOfferRequest createEditFixedPriceAndEnableRequest() {
        return newBuilder()
                .setId("83e8b2e2-51b6-4f39-a748-3ebd29c22aea")
                .setEditType(FIXED_PRICE_AND_ACTIVATION_STATE)
                .setPrice("30000.99")
                .setEnable(1)
                .build();
    }

    private static EditOfferRequest createEditPriceMarginRequest() {
        return newBuilder()
                .setId("83e8b2e2-51b6-4f39-a748-3ebd29c22aea")
                .setEditType(MKT_PRICE_MARGIN_ONLY)
                .setUseMarketBasedPrice(true)
                .setMarketPriceMarginPct(2.00)  // 2.00%
                .build();
    }

    private static EditOfferRequest createEditTriggerPriceRequest() {
        return newBuilder()
                .setId("83e8b2e2-51b6-4f39-a748-3ebd29c22aea")
                .setEditType(TRIGGER_PRICE_ONLY)
                .setTriggerPrice("29000.00")    // Trigger price is disabled when set to "0".
                .build();
    }

    private static void waitForOfferToBeRepublished(long ms) {
        try {
            // Wait for edited offer to be removed from offer-book, edited, and re-published.
            Thread.sleep(ms);
        } catch (InterruptedException ex) {
            // ignored
        }
    }
}
