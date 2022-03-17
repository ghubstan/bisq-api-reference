package bisq.rpccalls;

import bisq.proto.grpc.CreateCryptoCurrencyPaymentAccountRequest;
import bisq.proto.grpc.PaymentAccountsGrpc;
import io.grpc.ManagedChannelBuilder;

import static java.lang.System.out;

public class CreateCryptoCurrencyPaymentAccount extends BaseJavaExample {

    public static void main(String[] args) {
        try {
            var channel = ManagedChannelBuilder.forAddress("localhost", 9998).usePlaintext().build();
            addChannelShutdownHook(channel);
            var credentials = buildCallCredentials(getApiPassword());
            var stub = PaymentAccountsGrpc.newBlockingStub(channel).withCallCredentials(credentials);
            var xmrAddress = "4AsjtNXChh3Va58czCWHjn9S8ZFnsxggGZoSePauBHmSMr8vY5aBSqrPtQ9Y9M1iwkBHxcuTWXJsJ4NDATQjQJyKBXR7WP7";
            var request = CreateCryptoCurrencyPaymentAccountRequest.newBuilder()
                    .setAccountName("My Instant XMR Payment Account")
                    .setCurrencyCode("XMR")
                    .setAddress(xmrAddress)
                    .setTradeInstant(true)
                    .build();
            var response = stub.createCryptoCurrencyPaymentAccount(request);
            out.println("New XMR instant payment account: " + response.getPaymentAccount());
        } catch (Throwable t) {
            handleError(t);
        }
    }
}
