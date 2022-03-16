package rpccalls;

import bisq.proto.grpc.GetTransactionRequest;
import bisq.proto.grpc.WalletsGrpc;
import io.grpc.ManagedChannelBuilder;

import static java.lang.System.out;

public class GetTransaction extends BaseJavaExample {

    public static void main(String[] args) {
        try {
            var channel = ManagedChannelBuilder.forAddress("localhost", 9998).usePlaintext().build();
            addChannelShutdownHook(channel);
            var credentials = buildCallCredentials(getApiPassword());
            var stub = WalletsGrpc.newBlockingStub(channel).withCallCredentials(credentials);
            var request = GetTransactionRequest.newBuilder()
                    .setTxId("fef206f2ada53e70fd8430d130e43bc3994ce075d50ac1f4fda8182c40ef6bdd")
                    .build();
            var response = stub.getTransaction(request);
            out.println(response);
        } catch (Throwable t) {
            handleError(t);
        }
    }
}
