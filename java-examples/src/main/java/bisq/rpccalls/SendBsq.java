package bisq.rpccalls;

import bisq.proto.grpc.SendBsqRequest;
import bisq.proto.grpc.WalletsGrpc;
import io.grpc.ManagedChannelBuilder;

import static java.lang.System.out;

public class SendBsq extends BaseJavaExample {

    public static void main(String[] args) {
        try {
            var channel = ManagedChannelBuilder.forAddress("localhost", 9998).usePlaintext().build();
            addChannelShutdownHook(channel);
            var credentials = buildCallCredentials(getApiPassword());
            var stub = WalletsGrpc.newBlockingStub(channel).withCallCredentials(credentials);
            var request = SendBsqRequest.newBuilder()
                    .setAddress("Bbcrt1q9elrmtxtzpwt25zq2pmeeu6qk8029w404ad0xn")
                    .setAmount("50.50")
                    .setTxFeeRate("50")
                    .build();
            var response = stub.sendBsq(request);
            out.println(response);
        } catch (Throwable t) {
            handleError(t);
        }
    }
}
