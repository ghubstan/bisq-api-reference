package rpccalls;

import bisq.proto.grpc.GetOffersRequest;
import bisq.proto.grpc.OffersGrpc;
import io.grpc.ManagedChannelBuilder;

import static java.lang.System.out;

public class GetOffers extends BaseJavaExample {

    public static void main(String[] args) {
        try {
            var channel = ManagedChannelBuilder.forAddress("localhost", 9998).usePlaintext().build();
            addChannelShutdownHook(channel);
            var credentials = buildCallCredentials(getApiPassword());
            var stub = OffersGrpc.newBlockingStub(channel).withCallCredentials(credentials);
            var request = GetOffersRequest.newBuilder()
                    .setDirection("SELL")       // Available offers to sell BTC
                    .setCurrencyCode("JPY")     // for JPY
                    .build();
            var response = stub.getOffers(request);
            out.println(response);
        } catch (Throwable t) {
            handleError(t);
        }
    }
}
