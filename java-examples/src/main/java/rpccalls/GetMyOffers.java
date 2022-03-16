package rpccalls;

import bisq.proto.grpc.GetMyOffersRequest;
import bisq.proto.grpc.OffersGrpc;
import io.grpc.ManagedChannelBuilder;

import static java.lang.System.out;

public class GetMyOffers extends BaseJavaExample {

    public static void main(String[] args) {
        try {
            var channel = ManagedChannelBuilder.forAddress("localhost", 9998).usePlaintext().build();
            addChannelShutdownHook(channel);
            var credentials = buildCallCredentials(getApiPassword());
            var stub = OffersGrpc.newBlockingStub(channel).withCallCredentials(credentials);
            var request = GetMyOffersRequest.newBuilder()
                    .setDirection("BUY")        // Offers to buy BTC
                    .setCurrencyCode("USD")     // with USD
                    .build();
            var response = stub.getMyOffers(request);
            out.println(response);
        } catch (Throwable t) {
            handleError(t);
        }
    }
}
