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
        response = grpc_service_stub.GetAddressBalance.with_call(
            bisq_messages.GetAddressBalanceRequest(address='mwLYmweQf2dCgAqQCb3qU2UbxBycVBi2PW'),
            metadata=[('password', api_password)])
        address_balance_info = response[0].address_balance_info
        print('Address = {0}\nAvailable Balance = {1} sats\nUnused? {2}\nNum confirmations of most recent tx = {3}'
              .format(address_balance_info.address,
                      address_balance_info.balance,
                      address_balance_info.is_address_unused,
                      address_balance_info.num_confirmations))
    except grpc.RpcError as rpc_error:
        print('gRPC API Exception: %s', rpc_error)


if __name__ == '__main__':
    main()
