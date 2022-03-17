package bisq.rpccalls;

import bisq.proto.grpc.FailTradeRequest;
import bisq.proto.grpc.TradesGrpc;
import io.grpc.ManagedChannelBuilder;

import static java.lang.System.out;

public class FailTrade extends BaseJavaExample {

    public static void main(String[] args) {
        try {
            var channel = ManagedChannelBuilder.forAddress("localhost", 9998).usePlaintext().build();
            addChannelShutdownHook(channel);
            var credentials = buildCallCredentials(getApiPassword());
            var stub = TradesGrpc.newBlockingStub(channel).withCallCredentials(credentials);
            var request = FailTradeRequest.newBuilder()
                    .setTradeId("83e8b2e2-51b6-4f39-a748-3ebd29c22aea")
                    .build();
            stub.failTrade(request);
            out.println("Open trade has been moved to failed trades list.");
        } catch (Throwable t) {
            handleError(t);
        }
    }
}
