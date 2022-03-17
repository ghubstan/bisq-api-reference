package bisq.rpccalls;

import bisq.proto.grpc.VerifyBsqSentToAddressRequest;
import bisq.proto.grpc.WalletsGrpc;
import io.grpc.ManagedChannelBuilder;

import static java.lang.System.out;

public class VerifyBsqSentToAddress extends BaseJavaExample {

    public static void main(String[] args) {
        try {
            var channel = ManagedChannelBuilder.forAddress("localhost", 9998).usePlaintext().build();
            addChannelShutdownHook(channel);
            var credentials = buildCallCredentials(getApiPassword());
            var stub = WalletsGrpc.newBlockingStub(channel).withCallCredentials(credentials);
            var request = VerifyBsqSentToAddressRequest.newBuilder()
                    .setAddress("Bbcrt1q9elrmtxtzpwt25zq2pmeeu6qk8029w404ad0xn")
                    .setAmount("50.50")
                    .build();
            var response = stub.verifyBsqSentToAddress(request);
            out.println("Address did receive amount of BSQ: " + response.getIsAmountReceived());
        } catch (Throwable t) {
            handleError(t);
        }
    }
}
