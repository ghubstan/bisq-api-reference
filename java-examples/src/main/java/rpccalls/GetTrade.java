package rpccalls;

import bisq.proto.grpc.GetTradeRequest;
import bisq.proto.grpc.TradesGrpc;
import io.grpc.ManagedChannelBuilder;

import static java.lang.System.out;

public class GetTrade extends BaseJavaExample {

    public static void main(String[] args) {
        try {
            var channel = ManagedChannelBuilder.forAddress("localhost", 9998).usePlaintext().build();
            addChannelShutdownHook(channel);
            var credentials = buildCallCredentials(getApiPassword());
            var stub = TradesGrpc.newBlockingStub(channel).withCallCredentials(credentials);
            var request = GetTradeRequest.newBuilder()
                    .setTradeId("83e8b2e2-51b6-4f39-a748-3ebd29c22aea")
                    .build();
            var response = stub.getTrade(request);
            out.println("Trade: " + response.getTrade());
        } catch (Throwable t) {
            handleError(t);
        }
    }
}
