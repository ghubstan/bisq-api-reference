package bisq.rpccalls;

import bisq.proto.grpc.TradesGrpc;
import bisq.proto.grpc.WithdrawFundsRequest;
import io.grpc.ManagedChannelBuilder;

import static java.lang.System.out;

public class WithdrawFunds extends BaseJavaExample {

    public static void main(String[] args) {
        try {
            var channel = ManagedChannelBuilder.forAddress("localhost", 9998).usePlaintext().build();
            addChannelShutdownHook(channel);
            var credentials = buildCallCredentials(getApiPassword());
            var stub = TradesGrpc.newBlockingStub(channel).withCallCredentials(credentials);
            var request = WithdrawFundsRequest.newBuilder()
                    .setTradeId("83e8b2e2-51b6-4f39-a748-3ebd29c22aea")
                    .setAddress("bcrt1qqau7ad7lf8xx08mnxl709h6cdv4ma9u3ace5k2")
                    .setMemo("Optional memo saved with transaction in Bisq wallet.")
                    .build();
            stub.withdrawFunds(request);
            out.println("BTC trade proceeds have been sent to external bitcoin wallet.");
        } catch (Throwable t) {
            handleError(t);
        }
    }
}
