from builtins import print

import grpc

# from getpass import getpass
import python_examples.bisqapi.grpc_pb2 as bisq_messages
import python_examples.bisqapi.grpc_pb2_grpc as bisq_service


def main():
    grpc_channel = grpc.insecure_channel('localhost:9998')
    grpc_service_stub = bisq_service.OffersStub(grpc_channel)
    api_password: str = 'xyz'  # getpass("Enter API password: ")
    try:
        response = grpc_service_stub.GetMyBsqSwapOffers.with_call(
            bisq_messages.GetBsqSwapOffersRequest(direction='BUY'),  # My buy BTC for BSQ swap offers
            metadata=[('password', api_password)])
        offers = list(response[0].bsq_swap_offers)
        print('Response: ' + str(offers))
    except grpc.RpcError as rpc_error:
        print('gRPC API Exception: %s', rpc_error)


if __name__ == '__main__':
    main()
