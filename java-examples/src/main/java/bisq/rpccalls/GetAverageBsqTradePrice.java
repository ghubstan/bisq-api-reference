package bisq.rpccalls;

import bisq.proto.grpc.GetAverageBsqTradePriceRequest;
import bisq.proto.grpc.PriceGrpc;
import io.grpc.ManagedChannelBuilder;

import static java.lang.String.format;
import static java.lang.System.out;

public class GetAverageBsqTradePrice extends BaseJavaExample {

    public static void main(String[] args) {
        try {
            var channel = ManagedChannelBuilder.forAddress("localhost", 9998).usePlaintext().build();
            addChannelShutdownHook(channel);
            var credentials = buildCallCredentials(getApiPassword());
            var stub = PriceGrpc.newBlockingStub(channel).withCallCredentials(credentials);
            var request = GetAverageBsqTradePriceRequest.newBuilder().setDays(30).build();
            var reply = stub.getAverageBsqTradePrice(request);
            var price = reply.getPrice();
            out.println(format("30-day avg BTC price: %s, 30-day avg USD price: %s",
                    price.getBtcPrice(),
                    price.getUsdPrice()));
        } catch (Throwable t) {
            handleError(t);
        }
    }
}
