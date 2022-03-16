package rpccalls;

import bisq.proto.grpc.GetVersionGrpc;
import bisq.proto.grpc.GetVersionRequest;
import io.grpc.ManagedChannelBuilder;

import static java.lang.System.out;

public class GetVersion extends BaseJavaExample {

    public static void main(String[] args) {
        try {
            var channel = ManagedChannelBuilder.forAddress("localhost", 9998).usePlaintext().build();
            addChannelShutdownHook(channel);
            var credentials = buildCallCredentials(getApiPassword());
            var stub = GetVersionGrpc.newBlockingStub(channel).withCallCredentials(credentials);
            var request = GetVersionRequest.newBuilder().build();
            var reply = stub.getVersion(request);
            out.println(reply.getVersion());
        } catch (Throwable t) {
            handleError(t);
        }
    }
}
