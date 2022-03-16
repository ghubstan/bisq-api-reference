package rpccalls;

import bisq.proto.grpc.CreateOfferRequest;
import bisq.proto.grpc.OffersGrpc;
import io.grpc.ManagedChannelBuilder;

import static java.lang.System.out;

public class CreateOffer extends BaseJavaExample {

    public static void main(String[] args) {
        try {
            var channel = ManagedChannelBuilder.forAddress("localhost", 9998).usePlaintext().build();
            addChannelShutdownHook(channel);
            var credentials = buildCallCredentials(getApiPassword());
            var stub = OffersGrpc.newBlockingStub(channel).withCallCredentials(credentials);

            var fixedPriceOfferRequest = createFixedPriceEurOfferRequest();
            var fixedPriceOfferResponse = stub.createOffer(fixedPriceOfferRequest);
            out.println(fixedPriceOfferResponse);
            waitForOfferPreparation(5_000);

            var marketBasedPriceOfferRequest = createMarketBasedPriceEurOfferRequest();
            var marketBasedPriceOfferResponse = stub.createOffer(marketBasedPriceOfferRequest);
            out.println(marketBasedPriceOfferResponse);
            waitForOfferPreparation(5_000);

            var fixedPriceXmrOfferRequest = createFixedPriceXmrOfferRequest();
            var fixedPriceXmrOfferResponse = stub.createOffer(fixedPriceXmrOfferRequest);
            out.println(fixedPriceXmrOfferResponse);
        } catch (Throwable t) {
            handleError(t);
        }
    }

    private static CreateOfferRequest createFixedPriceEurOfferRequest() {
        return CreateOfferRequest.newBuilder()
                .setCurrencyCode("EUR")
                .setDirection("BUY")                // Buy BTC with EUR
                .setPrice("34500.00")
                .setAmount(12500000)                // Satoshis
                .setMinAmount(6250000)              // Satoshis
                .setBuyerSecurityDepositPct(15.00)  // 15%
                .setPaymentAccountId("f3c1ec8b-9761-458d-b13d-9039c6892413")
                .setMakerFeeCurrencyCode("BTC")     // Pay Bisq trade fee in BTC
                .build();
    }

    private static CreateOfferRequest createMarketBasedPriceEurOfferRequest() {
        return CreateOfferRequest.newBuilder()
                .setCurrencyCode("EUR")
                .setDirection("SELL")               // Sell BTC for EUR
                .setUseMarketBasedPrice(true)
                .setMarketPriceMarginPct(3.25)      // Sell at 3.25% above market BTC price in EUR
                .setAmount(12500000)                // Satoshis
                .setMinAmount(6250000)              // Satoshis
                .setBuyerSecurityDepositPct(15.00)  // 15%
                .setTriggerPrice("0")               // No trigger price
                .setPaymentAccountId("f3c1ec8b-9761-458d-b13d-9039c6892413")
                .setMakerFeeCurrencyCode("BSQ")     // Pay Bisq trade fee in BSQ
                .build();
    }

    private static CreateOfferRequest createFixedPriceXmrOfferRequest() {
        return CreateOfferRequest.newBuilder()
                .setCurrencyCode("XMR")
                .setDirection("BUY")                // Buy BTC with XMR
                .setPrice("0.005")                  // BTC price for 1 XMR
                .setAmount(12500000)                // Satoshis
                .setMinAmount(6250000)              // Satoshis
                .setBuyerSecurityDepositPct(33.00)  // 33%
                .setPaymentAccountId("g3c8ec8b-9aa1-458d-b66d-9039c6892413")
                .setMakerFeeCurrencyCode("BSQ")     // Pay Bisq trade fee in BSQ
                .build();
    }

    private static void waitForOfferPreparation(long ms) {
        try {
            // Wait new offer's preparation and wallet updates before attempting to create another offer.
            Thread.sleep(5_000);
        } catch (InterruptedException ex) {
            // ignored
        }
    }
}
