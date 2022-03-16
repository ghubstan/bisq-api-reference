package rpccalls;

import bisq.proto.grpc.GetPaymentMethodsRequest;
import bisq.proto.grpc.PaymentAccountsGrpc;
import io.grpc.ManagedChannelBuilder;

import static java.lang.System.out;

public class GetPaymentMethods extends BaseJavaExample {

    public static void main(String[] args) {
        try {
            var channel = ManagedChannelBuilder.forAddress("localhost", 9998).usePlaintext().build();
            addChannelShutdownHook(channel);
            var credentials = buildCallCredentials(getApiPassword());
            var stub = PaymentAccountsGrpc.newBlockingStub(channel).withCallCredentials(credentials);
            var request = GetPaymentMethodsRequest.newBuilder().build();
            var response = stub.getPaymentMethods(request);
            out.println("Response: " + response.getPaymentMethodsList());
        } catch (Throwable t) {
            handleError(t);
        }
    }
}
