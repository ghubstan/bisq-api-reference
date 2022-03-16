package rpccalls;

import bisq.proto.grpc.GetTradesRequest;
import bisq.proto.grpc.TradesGrpc;
import io.grpc.ManagedChannelBuilder;

import static bisq.proto.grpc.GetTradesRequest.Category.CLOSED;
import static java.lang.System.out;

public class GetTrades extends BaseJavaExample {

    public static void main(String[] args) {
        try {
            var channel = ManagedChannelBuilder.forAddress("localhost", 9998).usePlaintext().build();
            addChannelShutdownHook(channel);
            var credentials = buildCallCredentials(getApiPassword());
            var stub = TradesGrpc.newBlockingStub(channel).withCallCredentials(credentials);
            var request = GetTradesRequest.newBuilder()
                    .setCategory(CLOSED) // Or currently OPEN, or FAILED
                    .build();
            var response = stub.getTrades(request);
            out.println("Open trades: " + response.getTradesList());
        } catch (Throwable t) {
            handleError(t);
        }
    }
}
