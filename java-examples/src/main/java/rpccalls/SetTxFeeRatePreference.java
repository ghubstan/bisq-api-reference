package rpccalls;

import bisq.proto.grpc.SetTxFeeRatePreferenceRequest;
import bisq.proto.grpc.WalletsGrpc;
import io.grpc.ManagedChannelBuilder;

import static java.lang.System.out;

public class SetTxFeeRatePreference extends BaseJavaExample {

    public static void main(String[] args) {
        try {
            var channel = ManagedChannelBuilder.forAddress("localhost", 9998).usePlaintext().build();
            addChannelShutdownHook(channel);
            var credentials = buildCallCredentials(getApiPassword());
            var stub = WalletsGrpc.newBlockingStub(channel).withCallCredentials(credentials);
            var request = SetTxFeeRatePreferenceRequest.newBuilder()
                    .setTxFeeRatePreference(25)
                    .build();
            var response = stub.setTxFeeRatePreference(request);
            out.println(response);
        } catch (Throwable t) {
            handleError(t);
        }
    }
}
