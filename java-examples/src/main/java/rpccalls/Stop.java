package rpccalls;

import bisq.proto.grpc.ShutdownServerGrpc;
import bisq.proto.grpc.StopRequest;
import io.grpc.ManagedChannelBuilder;

import static java.lang.System.out;

public class Stop extends BaseJavaExample {

    public static void main(String[] args) {
        try {
            var channel = ManagedChannelBuilder.forAddress("localhost", 9998).usePlaintext().build();
            addChannelShutdownHook(channel);
            var credentials = buildCallCredentials(getApiPassword());
            var stub = ShutdownServerGrpc.newBlockingStub(channel).withCallCredentials(credentials);
            var request = StopRequest.newBuilder().build();
            stub.stop(request);
            out.println("Daemon is shutting down.");
        } catch (Throwable t) {
            handleError(t);
        }
    }
}
