package rpccalls;

import bisq.proto.grpc.GetPaymentAccountFormRequest;
import bisq.proto.grpc.PaymentAccountsGrpc;
import io.grpc.ManagedChannelBuilder;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;

import static java.lang.System.out;

public class GetPaymentAccountForm extends BaseJavaExample {

    public static void main(String[] args) {
        try {
            var channel = ManagedChannelBuilder.forAddress("localhost", 9998).usePlaintext().build();
            addChannelShutdownHook(channel);
            var credentials = buildCallCredentials(getApiPassword());
            var stub = PaymentAccountsGrpc.newBlockingStub(channel).withCallCredentials(credentials);
            var request = GetPaymentAccountFormRequest.newBuilder()
                    .setPaymentMethodId("SWIFT")
                    .build();
            var response = stub.getPaymentAccountForm(request);
            var paymentAccountFormJson = response.getPaymentAccountFormJson();
            File jsonFile = new File("/tmp/blank-swift-account-form.json");
            BufferedWriter writer = new BufferedWriter(new FileWriter(jsonFile));
            writer.write(paymentAccountFormJson);
            writer.close();
            out.println("Swift payment account saved to " + jsonFile.getAbsolutePath());
        } catch (Throwable t) {
            handleError(t);
        }
    }
}
