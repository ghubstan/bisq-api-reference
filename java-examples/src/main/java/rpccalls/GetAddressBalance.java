package rpccalls;

import bisq.proto.grpc.GetAddressBalanceRequest;
import bisq.proto.grpc.WalletsGrpc;
import io.grpc.ManagedChannelBuilder;

import static java.lang.System.out;

public class GetAddressBalance extends BaseJavaExample {

    public static void main(String[] args) {
        try {
            var channel = ManagedChannelBuilder.forAddress("localhost", 9998).usePlaintext().build();
            addChannelShutdownHook(channel);
            var credentials = buildCallCredentials(getApiPassword());
            var stub = WalletsGrpc.newBlockingStub(channel).withCallCredentials(credentials);
            var request = GetAddressBalanceRequest.newBuilder()
                    .setAddress("mwLYmweQf2dCgAqQCb3qU2UbxBycVBi2PW")
                    .build();
            var response = stub.getAddressBalance(request);
            out.println(response);
        } catch (Throwable t) {
            handleError(t);
        }
    }
}
