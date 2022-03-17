package bisq.rpccalls;

import bisq.proto.grpc.GetCryptoCurrencyPaymentMethodsRequest;
import bisq.proto.grpc.PaymentAccountsGrpc;
import io.grpc.ManagedChannelBuilder;

import static java.lang.System.out;

public class GetCryptoCurrencyPaymentMethods extends BaseJavaExample {
    // Note:  API currently supports only BSQ, BSQ Swap, and XMR trading.
    public static void main(String[] args) {
        try {
            var channel = ManagedChannelBuilder.forAddress("localhost", 9998).usePlaintext().build();
            addChannelShutdownHook(channel);
            var credentials = buildCallCredentials(getApiPassword());
            var stub = PaymentAccountsGrpc.newBlockingStub(channel).withCallCredentials(credentials);
            var request = GetCryptoCurrencyPaymentMethodsRequest.newBuilder().build();
            var response = stub.getCryptoCurrencyPaymentMethods(request);
            out.println("Response: " + response.getPaymentMethodsList());
        } catch (Throwable t) {
            handleError(t);
        }
    }
}
