import grpc

# from getpass import getpass
import bisq.api.grpc_pb2 as bisq_messages
import bisq.api.grpc_pb2_grpc as bisq_service


def main():
    grpc_channel = grpc.insecure_channel('localhost:9998')
    grpc_service_stub = bisq_service.PaymentAccountsStub(grpc_channel)
    api_password: str = 'xyz'  # getpass("Enter API password: ")
    try:
        response = grpc_service_stub.GetPaymentMethods.with_call(
            bisq_messages.GetPaymentMethodsRequest(),
            metadata=[('password', api_password)])
        payment_methods = list(response[0].payment_methods)
        print('Response: {0} payment methods'.format(len(payment_methods)))
        for payment_method in payment_methods:
            print('\t\t{0}'.format(payment_method.id))
    except grpc.RpcError as rpc_error:
        print('gRPC API Exception: %s', rpc_error)


if __name__ == '__main__':
    main()
