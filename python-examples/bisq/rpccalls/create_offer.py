# from getpass import getpass
import time
from builtins import print

import grpc

import bisq.api.grpc_pb2 as bisq_messages
import bisq.api.grpc_pb2_grpc as bisq_service


def main():
    grpc_channel = grpc.insecure_channel('localhost:9998')
    grpc_service_stub = bisq_service.OffersStub(grpc_channel)
    api_password: str = 'xyz'  # getpass("Enter API password: ")
    try:
        create_offer_request = fixed_price_usd_request()
        create_offer_response = grpc_service_stub.CreateOffer.with_call(create_offer_request,
                                                                        metadata=[('password', api_password)])
        print('New fixed-price offer: ' + str(create_offer_response[0].offer))
        time.sleep(3)  # Wait for new offer preparation and wallet updates before creating another offer.

        create_offer_request = market_based_price_usd_request()
        create_offer_response = grpc_service_stub.CreateOffer.with_call(create_offer_request,
                                                                        metadata=[('password', api_password)])
        print('New mkt price margin based offer: ' + str(create_offer_response[0].offer))
        time.sleep(3)  # Wait for new offer preparation and wallet updates before creating another offer.

        create_offer_request = fixed_price_xmr_request()
        create_offer_response = grpc_service_stub.CreateOffer.with_call(create_offer_request,
                                                                        metadata=[('password', api_password)])
        print('New XMR offer: ' + str(create_offer_response[0].offer))

    except grpc.RpcError as rpc_error:
        print('gRPC API Exception: %s', rpc_error)


def fixed_price_usd_request():
    # Create an offer to buy BTC with USD at a fixed price.
    return bisq_messages.CreateOfferRequest(direction='BUY',
                                            currency_code='USD',
                                            price='40000.00',
                                            amount=12500000,
                                            min_amount=6250000,
                                            buyer_security_deposit_pct=20.00,
                                            payment_account_id='af852e11-f2db-48bd-82f5-123047b41f0c',
                                            maker_fee_currency_code='BSQ')


def market_based_price_usd_request():
    # Create an offer to sell BTC for USD at a moving, market price margin.
    return bisq_messages.CreateOfferRequest(direction='SELL',
                                            currency_code='USD',
                                            use_market_based_price=True,
                                            market_price_margin_pct=2.50,
                                            amount=12500000,
                                            min_amount=6250000,
                                            buyer_security_deposit_pct=20.00,
                                            payment_account_id='af852e11-f2db-48bd-82f5-123047b41f0c',
                                            maker_fee_currency_code='BSQ')


def fixed_price_xmr_request():
    # Create an offer to buy BTC with XMR.
    return bisq_messages.CreateOfferRequest(direction='BUY',  # Buy BTC with XMR
                                            currency_code='XMR',
                                            price='0.005',  # Price of 1 XMR in BTC
                                            amount=12500000,
                                            min_amount=6250000,
                                            buyer_security_deposit_pct=20.00,
                                            payment_account_id='7d52d9b6-e943-4625-a063-f53b09381bf2',
                                            maker_fee_currency_code='BTC')


if __name__ == '__main__':
    main()
