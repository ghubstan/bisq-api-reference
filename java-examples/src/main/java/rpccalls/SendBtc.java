package rpccalls;

import bisq.proto.grpc.SendBtcRequest;
import bisq.proto.grpc.WalletsGrpc;
import io.grpc.ManagedChannelBuilder;

import static java.lang.System.out;

public class SendBtc extends BaseJavaExample {

    public static void main(String[] args) {
        try {
            var channel = ManagedChannelBuilder.forAddress("localhost", 9998).usePlaintext().build();
            addChannelShutdownHook(channel);
            var credentials = buildCallCredentials(getApiPassword());
            var stub = WalletsGrpc.newBlockingStub(channel).withCallCredentials(credentials);
            var request = SendBtcRequest.newBuilder()
                    .setAddress("bcrt1qqau7ad7lf8xx08mnxl709h6cdv4ma9u3ace5k2")
                    .setAmount("0.005")
                    .setTxFeeRate("50")
                    .setMemo("Optional memo.")
                    .build();
            var response = stub.sendBtc(request);
            out.println(response);
        } catch (Throwable t) {
            handleError(t);
        }
    }
}
