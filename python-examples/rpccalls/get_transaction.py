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
        response = grpc_service_stub.GetTransaction.with_call(
            bisq_messages.GetTransactionRequest(
                tx_id='907cf2b9ec2970653a9d7b5384b729037bfdf213d3fa38797704a7adb4c7217e'),
            metadata=[('password', api_password)])
        print('Response: ' + str(response[0].tx_info))
    except grpc.RpcError as rpc_error:
        print('gRPC API Exception: %s', rpc_error)


if __name__ == '__main__':
    main()
