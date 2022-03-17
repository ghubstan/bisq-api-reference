package bisq.rpccalls;

import bisq.proto.grpc.GetBsqSwapOffersRequest;
import bisq.proto.grpc.OffersGrpc;
import io.grpc.ManagedChannelBuilder;

import static java.lang.System.out;

public class GetBsqSwapOffers extends BaseJavaExample {

    public static void main(String[] args) {
        try {
            var channel = ManagedChannelBuilder.forAddress("localhost", 9998).usePlaintext().build();
            addChannelShutdownHook(channel);
            var credentials = buildCallCredentials(getApiPassword());
            var stub = OffersGrpc.newBlockingStub(channel).withCallCredentials(credentials);
            var request = GetBsqSwapOffersRequest.newBuilder()
                    .setDirection("SELL")   // Offers to sell (swap) BTC for BSQ.
                    .build();
            var response = stub.getBsqSwapOffers(request);
            out.println(response);
        } catch (Throwable t) {
            handleError(t);
        }
    }
}
