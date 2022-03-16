package rpccalls;

import bisq.proto.grpc.UnlockWalletRequest;
import bisq.proto.grpc.WalletsGrpc;
import io.grpc.ManagedChannelBuilder;

import static java.lang.System.out;

public class UnlockWallet extends BaseJavaExample {

    public static void main(String[] args) {
        try {
            var channel = ManagedChannelBuilder.forAddress("localhost", 9998).usePlaintext().build();
            addChannelShutdownHook(channel);
            var credentials = buildCallCredentials(getApiPassword());
            var stub = WalletsGrpc.newBlockingStub(channel).withCallCredentials(credentials);
            var request = UnlockWalletRequest.newBuilder()
                    .setPassword("abc")
                    .setTimeout(120)
                    .build();
            stub.unlockWallet(request);
            out.println("Wallet is unlocked.");
        } catch (Throwable t) {
            handleError(t);
        }
    }
}
