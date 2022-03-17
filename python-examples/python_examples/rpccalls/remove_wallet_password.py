from builtins import print

import grpc

# from getpass import getpass
import python_examples.bisqapi.grpc_pb2 as bisq_messages
import python_examples.bisqapi.grpc_pb2_grpc as bisq_service


def main():
    grpc_channel = grpc.insecure_channel('localhost:9998')
    grpc_service_stub = bisq_service.WalletsStub(grpc_channel)
    api_password: str = 'xyz'  # getpass("Enter API password: ")
    try:
        response = grpc_service_stub.RemoveWalletPassword.with_call(
            bisq_messages.RemoveWalletPasswordRequest(password='abc'),
            metadata=[('password', api_password)])
        print('Response: ' + str(response))
    except grpc.RpcError as rpc_error:
        print('gRPC API Exception: %s', rpc_error)


if __name__ == '__main__':
    main()
