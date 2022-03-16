package rpccalls;

import bisq.proto.grpc.GetBalancesRequest;
import bisq.proto.grpc.WalletsGrpc;
import io.grpc.ManagedChannelBuilder;

import static java.lang.System.out;

public class GetBalances extends BaseJavaExample {

    public static void main(String[] args) {
        try {
            var channel = ManagedChannelBuilder.forAddress("localhost", 9998).usePlaintext().build();
            addChannelShutdownHook(channel);
            var credentials = buildCallCredentials(getApiPassword());
            var stub = WalletsGrpc.newBlockingStub(channel).withCallCredentials(credentials);
            var request = GetBalancesRequest.newBuilder().build();
            var response = stub.getBalances(request);
            out.println("BSQ " + response.getBalances().getBsq());
            out.println("BTC " + response.getBalances().getBtc());
        } catch (Throwable t) {
            handleError(t);
        }
    }
}
