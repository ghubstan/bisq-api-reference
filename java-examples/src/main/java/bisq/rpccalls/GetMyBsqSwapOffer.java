package bisq.rpccalls;

import bisq.proto.grpc.GetMyOfferRequest;
import bisq.proto.grpc.OffersGrpc;
import io.grpc.ManagedChannelBuilder;

import static java.lang.System.out;

public class GetMyBsqSwapOffer extends BaseJavaExample {

    public static void main(String[] args) {
        try {
            var channel = ManagedChannelBuilder.forAddress("localhost", 9998).usePlaintext().build();
            addChannelShutdownHook(channel);
            var credentials = buildCallCredentials(getApiPassword());
            var stub = OffersGrpc.newBlockingStub(channel).withCallCredentials(credentials);
            var request = GetMyOfferRequest.newBuilder()
                    .setId("83e8b2e2-51b6-4f39-a748-3ebd29c22aea")
                    .build();
            var response = stub.getMyBsqSwapOffer(request);
            out.println(response);
        } catch (Throwable t) {
            handleError(t);
        }
    }
}
