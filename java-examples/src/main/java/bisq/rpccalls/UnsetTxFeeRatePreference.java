package bisq.rpccalls;

import bisq.proto.grpc.UnsetTxFeeRatePreferenceRequest;
import bisq.proto.grpc.WalletsGrpc;
import io.grpc.ManagedChannelBuilder;

import static java.lang.System.out;

public class UnsetTxFeeRatePreference extends BaseJavaExample {

    public static void main(String[] args) {
        try {
            var channel = ManagedChannelBuilder.forAddress("localhost", 9998).usePlaintext().build();
            addChannelShutdownHook(channel);
            var credentials = buildCallCredentials(getApiPassword());
            var stub = WalletsGrpc.newBlockingStub(channel).withCallCredentials(credentials);
            var request = UnsetTxFeeRatePreferenceRequest.newBuilder().build();
            var response = stub.unsetTxFeeRatePreference(request);
            out.println(response);
        } catch (Throwable t) {
            handleError(t);
        }
    }
}
