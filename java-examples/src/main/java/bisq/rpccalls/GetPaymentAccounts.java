package bisq.rpccalls;

import bisq.proto.grpc.GetPaymentAccountsRequest;
import bisq.proto.grpc.PaymentAccountsGrpc;
import io.grpc.ManagedChannelBuilder;

import static java.lang.System.out;

public class GetPaymentAccounts extends BaseJavaExample {

    public static void main(String[] args) {
        try {
            var channel = ManagedChannelBuilder.forAddress("localhost", 9998).usePlaintext().build();
            addChannelShutdownHook(channel);
            var credentials = buildCallCredentials(getApiPassword());
            var stub = PaymentAccountsGrpc.newBlockingStub(channel).withCallCredentials(credentials);
            var request = GetPaymentAccountsRequest.newBuilder().build();
            var response = stub.getPaymentAccounts(request);
            out.println("Response: " + response.getPaymentAccountsList());
        } catch (Throwable t) {
            handleError(t);
        }
    }
}
