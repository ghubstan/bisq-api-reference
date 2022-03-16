# from getpass import getpass
import time
from builtins import print

import grpc

import bisq.api.grpc_pb2 as bisq_messages
import bisq.api.grpc_pb2_grpc as bisq_service

EDITED_USD_OFFER_ID = '44736-16df6819-d98b-4f13-87dd-50087c464fac-184'


def main():
    grpc_channel = grpc.insecure_channel('localhost:9998')
    grpc_service_stub = bisq_service.OffersStub(grpc_channel)
    api_password: str = 'xyz'  # getpass("Enter API password: ")
    try:
        edit_offer_request = disable_offer_request()
        edit_offer_response = grpc_service_stub.EditOffer.with_call(edit_offer_request,
                                                                    metadata=[('password', api_password)])
        print('Offer is disabled.  Rpc response: ' + str(edit_offer_response))
        time.sleep(4)  # Wait for new offer preparation and wallet updates before creating another offer.

        edit_offer_request = enable_offer_request()
        edit_offer_response = grpc_service_stub.EditOffer.with_call(edit_offer_request,
                                                                    metadata=[('password', api_password)])
        print('Offer is enabled.  Rpc response: ' + str(edit_offer_response))
        time.sleep(4)  # Wait for new offer preparation and wallet updates before creating another offer.

        edit_offer_request = edit_fixed_price_request()
        edit_offer_response = grpc_service_stub.EditOffer.with_call(edit_offer_request,
                                                                    metadata=[('password', api_password)])
        print('Offer fixed-price has been changed.  Rpc response: ' + str(edit_offer_response))
        time.sleep(4)  # Wait for new offer preparation and wallet updates before creating another offer.

        edit_offer_request = edit_fixed_price_and_enable_request()
        edit_offer_response = grpc_service_stub.EditOffer.with_call(edit_offer_request,
                                                                    metadata=[('password', api_password)])
        print('Offer fixed-price has been changed, and offer is enabled.  Rpc response: ' + str(edit_offer_response))
        time.sleep(4)  # Wait for new offer preparation and wallet updates before creating another offer.

        # Change the fixed-price offer to a mkt price margin based offer
        edit_offer_request = edit_price_margin_request()
        edit_offer_response = grpc_service_stub.EditOffer.with_call(edit_offer_request,
                                                                    metadata=[('password', api_password)])
        print('Fixed-price offer is not a mkt price margin based offer.  Rpc response: ' + str(edit_offer_response))
        time.sleep(4)  # Wait for new offer preparation and wallet updates before creating another offer.

        # Set the trigger-price on a mkt price margin based offer
        edit_offer_request = edit_trigger_price_request()
        edit_offer_response = grpc_service_stub.EditOffer.with_call(edit_offer_request,
                                                                    metadata=[('password', api_password)])
        print('Offer trigger price is set.  Rpc response: ' + str(edit_offer_response))

    except grpc.RpcError as rpc_error:
        print('gRPC API Exception: %s', rpc_error)


def disable_offer_request():
    return bisq_messages.EditOfferRequest(
        id=EDITED_USD_OFFER_ID,
        edit_type=bisq_messages.EditOfferRequest.EditType.ACTIVATION_STATE_ONLY,
        enable=0)  # If enable=-1: ignore enable param, enable=0: disable offer, enable=1: enable offer


def enable_offer_request():
    return bisq_messages.EditOfferRequest(
        id=EDITED_USD_OFFER_ID,
        edit_type=bisq_messages.EditOfferRequest.EditType.ACTIVATION_STATE_ONLY,
        enable=1)  # If enable=-1: ignore enable param, enable=0: disable offer, enable=1: enable offer


def edit_fixed_price_request():
    return bisq_messages.EditOfferRequest(
        id=EDITED_USD_OFFER_ID,
        price='42000.50',
        edit_type=bisq_messages.EditOfferRequest.EditType.FIXED_PRICE_ONLY,
        enable=-1)  # If enable=-1: ignore enable param, enable=0: disable offer, enable=1: enable offer


def edit_fixed_price_and_enable_request():
    return bisq_messages.EditOfferRequest(
        id=EDITED_USD_OFFER_ID,
        price='43000.50',
        edit_type=bisq_messages.EditOfferRequest.EditType.FIXED_PRICE_AND_ACTIVATION_STATE,
        enable=1)  # If enable=-1: ignore enable param, enable=0: disable offer, enable=1: enable offer


def edit_price_margin_request():
    return bisq_messages.EditOfferRequest(
        id=EDITED_USD_OFFER_ID,
        use_market_based_price=True,
        market_price_margin_pct=5.00,
        edit_type=bisq_messages.EditOfferRequest.EditType.MKT_PRICE_MARGIN_ONLY,
        enable=-1)  # If enable=-1: ignore enable param, enable=0: disable offer, enable=1: enable offer


def edit_trigger_price_request():
    return bisq_messages.EditOfferRequest(
        id=EDITED_USD_OFFER_ID,
        trigger_price='40000.0000',
        edit_type=bisq_messages.EditOfferRequest.EditType.TRIGGER_PRICE_ONLY,
        enable=-1)  # If enable=-1: ignore enable param, enable=0: disable offer, enable=1: enable offer


if __name__ == '__main__':
    main()
