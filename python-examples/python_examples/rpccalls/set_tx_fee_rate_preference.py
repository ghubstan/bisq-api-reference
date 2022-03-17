# from getpass import getpass
from builtins import print

import grpc

import python_examples.bisqapi.grpc_pb2 as bisq_messages
import python_examples.bisqapi.grpc_pb2_grpc as bisq_service


def main():
    grpc_channel = grpc.insecure_channel('localhost:9998')
    grpc_service_stub = bisq_service.WalletsStub(grpc_channel)
    api_password: str = 'xyz'  # getpass("Enter API password: ")
    try:
        response = grpc_service_stub.SetTxFeeRatePreference.with_call(
            bisq_messages.SetTxFeeRatePreferenceRequest(tx_fee_rate_preference=20),
            metadata=[('password', api_password)])
        print('Response: ' + str(response[0].tx_fee_rate_info))
    except grpc.RpcError as rpc_error:
        print('gRPC API Exception: %s', rpc_error)


if __name__ == '__main__':
    main()
