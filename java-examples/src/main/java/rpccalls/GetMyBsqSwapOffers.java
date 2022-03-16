package rpccalls;

import bisq.proto.grpc.GetBsqSwapOffersRequest;
import bisq.proto.grpc.OffersGrpc;
import io.grpc.ManagedChannelBuilder;

import static java.lang.System.out;

public class GetMyBsqSwapOffers extends BaseJavaExample {

    public static void main(String[] args) {
        try {
            var channel = ManagedChannelBuilder.forAddress("localhost", 9998).usePlaintext().build();
            addChannelShutdownHook(channel);
            var credentials = buildCallCredentials(getApiPassword());
            var stub = OffersGrpc.newBlockingStub(channel).withCallCredentials(credentials);
            var request = GetBsqSwapOffersRequest.newBuilder()
                    .setDirection("BUY")   // Offers to buy (swap) BTC for BSQ.
                    .build();
            var response = stub.getBsqSwapOffers(request);
            out.println(response);
        } catch (Throwable t) {
            handleError(t);
        }
    }
}
