/*
 * This file is part of Bisq.
 *
 * Bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.bots;

import bisq.proto.grpc.*;
import io.grpc.CallCredentials;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import lombok.extern.slf4j.Slf4j;

import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * gRPC Service Stubs -- all blocking.
 */
@Slf4j
final class GrpcStubs {

    public final DisputeAgentsGrpc.DisputeAgentsBlockingStub disputeAgentsService;
    public final HelpGrpc.HelpBlockingStub helpService;
    public final GetVersionGrpc.GetVersionBlockingStub versionService;
    public final OffersGrpc.OffersBlockingStub offersService;
    public final PaymentAccountsGrpc.PaymentAccountsBlockingStub paymentAccountsService;
    public final PriceGrpc.PriceBlockingStub priceService;
    public final ShutdownServerGrpc.ShutdownServerBlockingStub shutdownService;
    public final TradesGrpc.TradesBlockingStub tradesService;
    public final WalletsGrpc.WalletsBlockingStub walletsService;

    private final ManagedChannel channel;

    public GrpcStubs(String apiHost, int apiPort, String apiPassword) {
        CallCredentials credentials = new PasswordCallCredentials(apiPassword);

        this.channel = ManagedChannelBuilder.forAddress(apiHost, apiPort).usePlaintext().build();
        Runtime.getRuntime().addShutdownHook(new Thread(this::close));

        this.disputeAgentsService = DisputeAgentsGrpc.newBlockingStub(channel).withCallCredentials(credentials);
        this.helpService = HelpGrpc.newBlockingStub(channel).withCallCredentials(credentials);
        this.versionService = GetVersionGrpc.newBlockingStub(channel).withCallCredentials(credentials);
        this.offersService = OffersGrpc.newBlockingStub(channel).withCallCredentials(credentials);
        this.paymentAccountsService = PaymentAccountsGrpc.newBlockingStub(channel).withCallCredentials(credentials);
        this.priceService = PriceGrpc.newBlockingStub(channel).withCallCredentials(credentials);
        this.shutdownService = ShutdownServerGrpc.newBlockingStub(channel).withCallCredentials(credentials);
        this.tradesService = TradesGrpc.newBlockingStub(channel).withCallCredentials(credentials);
        this.walletsService = WalletsGrpc.newBlockingStub(channel).withCallCredentials(credentials);
    }

    public void close() {
        try {
            if (!channel.isShutdown()) {
                log.debug("Shutting down bot's grpc channel.");
                channel.shutdown().awaitTermination(1, SECONDS);
                log.debug("Bot channel shutdown complete.");
            } else {
                log.warn("Bot channel already shut down!");
            }
        } catch (InterruptedException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
