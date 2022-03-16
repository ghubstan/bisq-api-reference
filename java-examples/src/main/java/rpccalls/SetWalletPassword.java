package rpccalls;

import bisq.proto.grpc.SetWalletPasswordRequest;
import bisq.proto.grpc.WalletsGrpc;
import io.grpc.ManagedChannelBuilder;

import static java.lang.System.out;

public class SetWalletPassword extends BaseJavaExample {

    public static void main(String[] args) {
        try {
            var channel = ManagedChannelBuilder.forAddress("localhost", 9998).usePlaintext().build();
            addChannelShutdownHook(channel);
            var credentials = buildCallCredentials(getApiPassword());
            var stub = WalletsGrpc.newBlockingStub(channel).withCallCredentials(credentials);
            var request = SetWalletPasswordRequest.newBuilder().setPassword("abc").build();
            stub.setWalletPassword(request);
            out.println("Wallet encryption password is set.");
        } catch (Throwable t) {
            handleError(t);
        }
    }
}
