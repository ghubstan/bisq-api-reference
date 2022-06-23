package bisq.rpccalls;

import bisq.proto.grpc.GetNetworkRequest;
import bisq.proto.grpc.WalletsGrpc;
import io.grpc.ManagedChannelBuilder;

import static java.lang.System.out;

public class GetNetwork extends BaseJavaExample {

    public static void main(String[] args) {
        try {
            var channel = ManagedChannelBuilder.forAddress("localhost", 9998).usePlaintext().build();
            addChannelShutdownHook(channel);
            var credentials = buildCallCredentials(getApiPassword());
            var stub = WalletsGrpc.newBlockingStub(channel).withCallCredentials(credentials);
            var request = GetNetworkRequest.newBuilder().build();
            var reply = stub.getNetwork(request);
            out.println(reply.getNetwork());
        } catch (Throwable t) {
            handleError(t);
        }
    }
}
