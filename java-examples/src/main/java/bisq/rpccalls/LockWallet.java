package bisq.rpccalls;

import bisq.proto.grpc.LockWalletRequest;
import bisq.proto.grpc.WalletsGrpc;
import io.grpc.ManagedChannelBuilder;

import static java.lang.System.out;

public class LockWallet extends BaseJavaExample {

    public static void main(String[] args) {
        try {
            var channel = ManagedChannelBuilder.forAddress("localhost", 9998).usePlaintext().build();
            addChannelShutdownHook(channel);
            var credentials = buildCallCredentials(getApiPassword());
            var stub = WalletsGrpc.newBlockingStub(channel).withCallCredentials(credentials);
            var request = LockWalletRequest.newBuilder().build();
            stub.lockWallet(request);
            out.println("Wallet is locked.");
        } catch (Throwable t) {
            handleError(t);
        }
    }
}
