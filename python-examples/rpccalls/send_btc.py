from builtins import print

import grpc

# from getpass import getpass
import bisq.api.grpc_pb2 as bisq_messages
import bisq.api.grpc_pb2_grpc as bisq_service


def main():
    grpc_channel = grpc.insecure_channel('localhost:9998')
    grpc_service_stub = bisq_service.WalletsStub(grpc_channel)
    api_password: str = 'xyz'  # getpass("Enter API password: ")
    try:
        response = grpc_service_stub.SendBtc.with_call(
            bisq_messages.SendBtcRequest(
                address='bcrt1qr3tjm77z3qzkf4kstj9v3yw9ewhjqjefz3c48y',
                amount='0.006',
                tx_fee_rate='15',
                memo='Optional memo saved with transaction in Bisq wallet.'),
            metadata=[('password', api_password)])
        print('Response: ' + str(response[0].tx_info))
    except grpc.RpcError as rpc_error:
        print('gRPC API Exception: %s', rpc_error)


if __name__ == '__main__':
    main()
