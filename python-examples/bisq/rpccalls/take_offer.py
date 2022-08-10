# from getpass import getpass


import grpc

import bisq.api.grpc_pb2 as bisq_messages
import bisq.api.grpc_pb2_grpc as bisq_service


def main():
    grpc_channel = grpc.insecure_channel('localhost:9998')
    grpc_offers_service_stub = bisq_service.OffersStub(grpc_channel)
    grpc_trades_service_stub = bisq_service.TradesStub(grpc_channel)
    api_password: str = 'xyz'  # getpass("Enter API password: ")
    try:
        # We need to send our payment account id and an (optional) taker fee currency code if offer
        # is not a BSQ swap offer.  Find out by calling GetOfferCategory before taking the offer.
        get_offer_category_response = grpc_offers_service_stub.GetOfferCategory.with_call(
            bisq_messages.GetOfferCategoryRequest(id='MGAQRIJJ-3aba77be-588c-4f98-839e-53fac183b823-194'),
            metadata=[('password', api_password)])
        offer_category = get_offer_category_response[0].offer_category
        is_bsq_swap = offer_category == bisq_messages.GetOfferCategoryReply.BSQ_SWAP
        take_offer_request = bisq_messages.TakeOfferRequest(
            offer_id='MGAQRIJJ-3aba77be-588c-4f98-839e-53fac183b823-194',
            # 10 million satoshis = 0.1 BTC
            amount=10000000
        )
        if not is_bsq_swap:
            take_offer_request.payment_account_id = '88892636-81a1-4864-a396-038297e112cf'
            take_offer_request.taker_fee_currency_code = 'BSQ'
        response = grpc_trades_service_stub.TakeOffer.with_call(
            take_offer_request,
            metadata=[('password', api_password)])
        print('Response: ' + str(response))
    except grpc.RpcError as rpc_error:
        print('gRPC API Exception: %s', rpc_error)


if __name__ == '__main__':
    main()
