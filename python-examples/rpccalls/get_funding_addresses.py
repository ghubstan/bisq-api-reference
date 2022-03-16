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
        response = grpc_service_stub.GetFundingAddresses.with_call(
            bisq_messages.GetFundingAddressesRequest(),
            metadata=[('password', api_password)])
        funding_addresses = list(response[0].address_balance_info)
        print('Response contains {0} funding addresses:'.format(len(funding_addresses)))
        for address_balance_info in funding_addresses:
            print('Address = {0}  Available Balance = {1} sats  Unused? {2}  Num confirmations of most recent tx = {3}'
                  .format(address_balance_info.address,
                          address_balance_info.balance,
                          address_balance_info.is_address_unused,
                          address_balance_info.num_confirmations))
    except grpc.RpcError as rpc_error:
        print('gRPC API Exception: %s', rpc_error)


if __name__ == '__main__':
    main()
