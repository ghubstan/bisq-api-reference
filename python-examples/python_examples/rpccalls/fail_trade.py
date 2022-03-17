from builtins import print

import grpc

# from getpass import getpass
import python_examples.bisqapi.grpc_pb2 as bisq_messages
import python_examples.bisqapi.grpc_pb2_grpc as bisq_service


def main():
    grpc_channel = grpc.insecure_channel('localhost:9998')
    grpc_service_stub = bisq_service.TradesStub(grpc_channel)
    api_password: str = 'xyz'  # getpass("Enter API password: ")
    try:
        response = grpc_service_stub.FailTrade.with_call(
            bisq_messages.FailTradeRequest(trade_id='HDCXKUR-1cfb39e9-68b9-4772-8ae0-abceb8339c90-184'),
            metadata=[('password', api_password)])
        print('Response: ' + str(response))
    except grpc.RpcError as rpc_error:
        print('gRPC API Exception: %s', rpc_error)


if __name__ == '__main__':
    main()
