package bisq.rpccalls;

import bisq.proto.grpc.MarketPriceRequest;
import bisq.proto.grpc.PriceGrpc;
import io.grpc.ManagedChannelBuilder;

import static java.lang.System.out;

public class GetMarketPrice extends BaseJavaExample {

    public static void main(String[] args) {
        try {
            var channel = ManagedChannelBuilder.forAddress("localhost", 9998).usePlaintext().build();
            addChannelShutdownHook(channel);
            var credentials = buildCallCredentials(getApiPassword());
            var stub = PriceGrpc.newBlockingStub(channel).withCallCredentials(credentials);
            var request = MarketPriceRequest.newBuilder()
                    .setCurrencyCode("XMR")
                    .build();
            var response = stub.getMarketPrice(request);
            out.println("Most recently available XMR market price: " + response.getPrice());
        } catch (Throwable t) {
            handleError(t);
        }
    }
}
