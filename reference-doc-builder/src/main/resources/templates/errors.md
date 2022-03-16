# Errors

Errors sent from the Java-based gRPC daemon to gRPC clients are instances
of [StatusRuntimeException](https://github.com/grpc/grpc-java/blob/master/api/src/main/java/io/grpc/StatusRuntimeException.java)
. For non-Java gRPC clients, the equivelant gRPC error type is sent. Exceptions sent to gRPC clients contain
a [Status.Code](https://github.com/grpc/grpc-java/blob/master/api/src/main/java/io/grpc/Status.java) to aid client-side
error handling. The Bisq API daemon does not use all sixteen of the gRPC status codes, below are the currently used
status codes.

Code | Value | Description
 ------------- |-------| ------------- 
UNKNOWN | 2     | An unexpected error occurred in the daemon, and it was not mapped to a meaningful status code.
INVALID_ARGUMENT | 3     | An invalid parameter value was sent to the daemon.
NOT_FOUND | 5     | A requested entity was not found.
ALREADY_EXISTS | 6     | An attempt to change some value or state in the daemon failed because the value or state already exists.
FAILED_PRECONDITION | 9     | An attempted operation failed because some pre-condition was not met.
UNIMPLEMENTED | 12    | An attempt was made to perform an unsupported operation.
UNAVAILABLE | 14    | Some resource is not available at the time requested.
UNAUTHENTICATED | 16    | The gRPC client did not correctly authenticate to the daemon.

