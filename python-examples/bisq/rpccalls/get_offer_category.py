# from getpass import getpass


import grpc

import bisq.api.grpc_pb2 as bisq_messages
import bisq.api.grpc_pb2_grpc as bisq_service


def main():
    grpc_channel = grpc.insecure_channel('localhost:9998')
    grpc_service_stub = bisq_service.OffersStub(grpc_channel)
    api_password: str = 'xyz'  # getpass("Enter API password: ")
    try:
        response = grpc_service_stub.GetOfferCategory.with_call(
            bisq_messages.GetOfferCategoryRequest(
                id='VZLGFPV-e8dd2f8c-fc90-4509-8f30-e0bb95815b46-184',
                is_my_offer=False),
            metadata=[('password', api_password)])
        print('Response: ' + str(response[0].offer_category))
    except grpc.RpcError as rpc_error:
        print('gRPC API Exception: %s', rpc_error)


if __name__ == '__main__':
    main()
