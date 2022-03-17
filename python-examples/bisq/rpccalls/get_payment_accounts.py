from builtins import print

import grpc

# from getpass import getpass
import bisq.api.grpc_pb2 as bisq_messages
import bisq.api.grpc_pb2_grpc as bisq_service


def main():
    grpc_channel = grpc.insecure_channel('localhost:9998')
    grpc_service_stub = bisq_service.PaymentAccountsStub(grpc_channel)
    api_password: str = 'xyz'  # getpass("Enter API password: ")
    try:
        response = grpc_service_stub.GetPaymentAccounts.with_call(
            bisq_messages.GetPaymentAccountsRequest(),
            metadata=[('password', api_password)])
        payment_accounts = list(response[0].payment_accounts)
        print('Response: {0} payment accounts'.format(len(payment_accounts)))
        print('\t\t{0:<40} {1:<24} {2:<20} {3:<8}'.format('ID', 'Name', 'Payment Method', 'Trade Currency'))
        for payment_account in payment_accounts:
            print('\t\t{0:<40} {1:<24} {2:<20} {3:<8}'
                  .format(payment_account.id,
                          payment_account.account_name,
                          payment_account.payment_method.id,
                          payment_account.selected_trade_currency.code))
    except grpc.RpcError as rpc_error:
        print('gRPC API Exception: %s', rpc_error)


if __name__ == '__main__':
    main()
