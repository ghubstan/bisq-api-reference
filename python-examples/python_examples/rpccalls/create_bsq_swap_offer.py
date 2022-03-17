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
        response = grpc_service_stub.CreateBsqSwapOffer.with_call(
            bisq_messages.CreateBsqSwapOfferRequest(
                direction='SELL',  # Buy BTC with BSQ
                price='0.00005',  # Price of 1 BSQ in BTC
                amount=6250000,  # Satoshis
                min_amount=3125000),  # Optional parameter cannot be 0 Satoshis.
            metadata=[('password', api_password)])
        print('New BSQ swap offer: ' + str(response[0].bsq_swap_offer))
    except grpc.RpcError as rpc_error:
        print('gRPC API Exception: %s', rpc_error)


if __name__ == '__main__':
    main()
