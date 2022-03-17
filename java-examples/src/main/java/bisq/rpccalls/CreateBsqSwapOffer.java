package bisq.rpccalls;

import bisq.proto.grpc.CreateBsqSwapOfferRequest;
import bisq.proto.grpc.OffersGrpc;
import io.grpc.ManagedChannelBuilder;

import static java.lang.System.out;

public class CreateBsqSwapOffer extends BaseJavaExample {

    public static void main(String[] args) {
        try {
            var channel = ManagedChannelBuilder.forAddress("localhost", 9998).usePlaintext().build();
            addChannelShutdownHook(channel);
            var credentials = buildCallCredentials(getApiPassword());
            var stub = OffersGrpc.newBlockingStub(channel).withCallCredentials(credentials);
            var request = CreateBsqSwapOfferRequest.newBuilder()
                    .setDirection("BUY")    // Buy BTC with BSQ
                    .setAmount(12500000)    // Satoshis
                    .setMinAmount(6250000)  // Satoshis
                    .setPrice("0.00005")    // Price of 1 BSQ in BTC
                    .build();
            var response = stub.createBsqSwapOffer(request);
            out.println(response);
        } catch (Throwable t) {
            handleError(t);
        }
    }
}
