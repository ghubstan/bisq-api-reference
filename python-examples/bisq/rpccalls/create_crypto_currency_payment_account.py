import grpc

# from getpass import getpass
import bisq.api.grpc_pb2 as bisq_messages
import bisq.api.grpc_pb2_grpc as bisq_service


def main():
    grpc_channel = grpc.insecure_channel('localhost:9998')
    grpc_service_stub = bisq_service.PaymentAccountsStub(grpc_channel)
    api_password: str = 'xyz'  # getpass("Enter API password: ")
    try:
        response = grpc_service_stub.CreateCryptoCurrencyPaymentAccount.with_call(
            bisq_messages.CreateCryptoCurrencyPaymentAccountRequest(
                account_name='name',
                currency_code='XMR',
                address='472CJ9TADoeVabhAe6byZQN4yqAFA4morKiyzb8DfLTj4hcQvsXNHxJUNYMw1JDmMALkQ3Bwmyn4aZYST7DzEw9nUeUTKVL',
                trade_instant=False),
            metadata=[('password', api_password)])
        print('Response: ' + str(response[0].payment_account))
    except grpc.RpcError as rpc_error:
        print('gRPC API Exception: %s', rpc_error)


if __name__ == '__main__':
    main()
