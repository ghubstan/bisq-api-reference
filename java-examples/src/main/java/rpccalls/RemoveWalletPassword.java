package rpccalls;

import bisq.proto.grpc.RemoveWalletPasswordRequest;
import bisq.proto.grpc.WalletsGrpc;
import io.grpc.ManagedChannelBuilder;

import static java.lang.System.out;

public class RemoveWalletPassword extends BaseJavaExample {

    public static void main(String[] args) {
        try {
            var channel = ManagedChannelBuilder.forAddress("localhost", 9998).usePlaintext().build();
            addChannelShutdownHook(channel);
            var credentials = buildCallCredentials(getApiPassword());
            var stub = WalletsGrpc.newBlockingStub(channel).withCallCredentials(credentials);
            var request = RemoveWalletPasswordRequest.newBuilder().setPassword("abc").build();
            stub.removeWalletPassword(request);
            out.println("Wallet encryption password is removed.");
        } catch (Throwable t) {
            handleError(t);
        }
    }
}
