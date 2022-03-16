package rpccalls;

import io.grpc.CallCredentials;
import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.StatusRuntimeException;

import java.util.Scanner;
import java.util.concurrent.Executor;

import static io.grpc.Metadata.ASCII_STRING_MARSHALLER;
import static io.grpc.Status.UNAUTHENTICATED;
import static java.lang.System.*;
import static java.util.concurrent.TimeUnit.SECONDS;

public class BaseJavaExample {

    static void addChannelShutdownHook(ManagedChannel channel) {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                channel.shutdown().awaitTermination(1, SECONDS);
            } catch (InterruptedException ex) {
                throw new IllegalStateException("Error shutting down gRPC channel.", ex);
            }
        }));
    }

    static String getApiPassword() {
        Scanner scanner = new Scanner(in);
        out.println("Enter api password:");
        var apiPassword = "xyz";    // scanner.nextLine();
        scanner.close();
        return apiPassword;
    }

    static CallCredentials buildCallCredentials(String apiPassword) {
        return new CallCredentials() {
            @Override
            public void applyRequestMetadata(RequestInfo requestInfo,
                                             Executor appExecutor,
                                             MetadataApplier metadataApplier) {
                appExecutor.execute(() -> {
                    try {
                        var headers = new Metadata();
                        var passwordKey = Metadata.Key.of("password", ASCII_STRING_MARSHALLER);
                        headers.put(passwordKey, apiPassword);
                        metadataApplier.apply(headers);
                    } catch (Throwable ex) {
                        metadataApplier.fail(UNAUTHENTICATED.withCause(ex));
                    }
                });
            }

            @Override
            public void thisUsesUnstableApi() {
            }
        };
    }

    static void handleError(Throwable t) {
        if (t instanceof StatusRuntimeException) {
            var grpcErrorStatus = ((StatusRuntimeException) t).getStatus();
            err.println(grpcErrorStatus.getCode() + ": " + grpcErrorStatus.getDescription());
        } else {
            err.println("Error: " + t);
        }
    }
}