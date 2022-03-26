# from getpass import getpass


import grpc

import bisq.api.grpc_pb2 as bisq_messages
import bisq.api.grpc_pb2_grpc as bisq_service


def main():
    grpc_channel = grpc.insecure_channel('localhost:9999')
    grpc_offers_service_stub = bisq_service.OffersStub(grpc_channel)
    grpc_trades_service_stub = bisq_service.TradesStub(grpc_channel)
    api_password: str = 'xyz'  # getpass("Enter API password: ")
    try:
        # We need to send our payment account id and an (optional) taker fee currency code if offer
        # is not a BSQ swap offer.  Find out by calling GetOfferCategory before taking the offer.
        get_offer_category_response = grpc_offers_service_stub.GetOfferCategory.with_call(
            bisq_messages.GetOfferCategoryRequest(id='4940749-73a2e9c3-d5b9-440a-a05d-9feb8e8805f0-182'),
            metadata=[('password', api_password)])
        offer_category = get_offer_category_response[0].offer_category
        is_bsq_swap = offer_category == bisq_messages.GetOfferCategoryReply.BSQ_SWAP
        take_offer_request = bisq_messages.TakeOfferRequest(offer_id='4940749-73a2e9c3-d5b9-440a-a05d-9feb8e8805f0-182')
        if not is_bsq_swap:
            take_offer_request.payment_account_id = '44838060-ddb5-4fa4-8b34-c128a655316e'
            take_offer_request.taker_fee_currency_code = 'BSQ'
        response = grpc_trades_service_stub.TakeOffer.with_call(
            take_offer_request,
            metadata=[('password', api_password)])
        print('Response: ' + str(response))
    except grpc.RpcError as rpc_error:
        print('gRPC API Exception: %s', rpc_error)


if __name__ == '__main__':
    main()
