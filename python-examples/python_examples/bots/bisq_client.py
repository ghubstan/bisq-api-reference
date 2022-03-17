import operator
import time
from datetime import datetime, timezone
from decimal import *

import grpc

import python_examples.bisqapi.grpc_pb2 as bisq_messages
import python_examples.bisqapi.grpc_pb2_grpc as bisq_service
from logger import log

# For more channel options, please see https://grpc.io/grpc/core/group__grpc__arg__keys.html
# And see https://www.cs.mcgill.ca/~mxia3/2019/02/23/Using-gRPC-in-Production
CHANNEL_OPTIONS = [('grpc.lb_policy_name', 'pick_first'),
                   ('grpc.enable_retries', 0),
                   ('grpc.keepalive_timeout_ms', 10000)]


class BisqClient(object):
    def __init__(self, host, port, api_password):
        self.host = host
        self.port = port
        self.api_password = api_password
        self.grpc_channel = None
        self.offers_stub = None
        self.payment_accts_stub = None
        self.trades_stub = None
        self.version_stub = None
        self.wallets_stub = None
        self.open_channel()

    def open_channel(self):
        self.grpc_channel = grpc.insecure_channel(self.host + ':' + str(self.port), options=CHANNEL_OPTIONS)
        self.offers_stub = bisq_service.OffersStub(self.grpc_channel)
        self.payment_accts_stub = bisq_service.PaymentAccountsStub(self.grpc_channel)
        self.trades_stub = bisq_service.TradesStub(self.grpc_channel)
        self.version_stub = bisq_service.GetVersionStub(self.grpc_channel)
        self.wallets_stub = bisq_service.WalletsStub(self.grpc_channel)

    def close_channel(self):
        log.info('Closing gRPC channel')
        self.grpc_channel.close()
        time.sleep(0.5)
        self.grpc_channel = None

    def is_connected(self):
        return self.grpc_channel is not None

    def create_margin_priced_offer(self, currency_code,
                                   direction,
                                   market_price_margin_pct,
                                   amount,
                                   min_amount,
                                   payment_account_id):
        try:
            response = self.offers_stub.CreateOffer.with_call(
                bisq_messages.CreateOfferRequest(
                    currency_code=currency_code,
                    direction=direction,
                    use_market_based_price=True,
                    market_price_margin_pct=market_price_margin_pct,
                    amount=amount,
                    min_amount=min_amount,
                    buyer_security_deposit_pct=15.00,
                    payment_account_id=payment_account_id),
                metadata=[('password', self.api_password)])
            return response[0].offer
        except grpc.RpcError as rpc_error:
            print('gRPC API Exception: %s', rpc_error)

    def create_bsqswap_offer(self, direction, price_str, amount, min_amount):
        try:
            response = self.offers_stub.CreateBsqSwapOffer.with_call(
                bisq_messages.CreateBsqSwapOfferRequest(
                    direction=direction,
                    price=price_str,
                    amount=amount,
                    min_amount=min_amount),
                metadata=[('password', self.api_password)])
            return response[0].bsq_swap_offer
        except grpc.RpcError as rpc_error:
            print('gRPC API Exception: %s', rpc_error)

    def edit_offer_fixed_price(self, offer, fixed_price):
        try:
            self.offers_stub.EditOffer.with_call(
                bisq_messages.EditOfferRequest(
                    id=offer.id,
                    price=fixed_price,
                    edit_type=bisq_messages.EditOfferRequest.EditType.FIXED_PRICE_ONLY,
                    enable=-1),  # enable=-1 means offer activation state remains unchanged
                metadata=[('password', self.api_password)])
        except grpc.RpcError as rpc_error:
            print('gRPC API Exception: %s', rpc_error)

    def cancel_offer(self, offer_id):
        try:
            self.offers_stub.CancelOffer.with_call(
                bisq_messages.CancelOfferRequest(id=offer_id),
                metadata=[('password', self.api_password)])
        except grpc.RpcError as rpc_error:
            print('gRPC API Exception: %s', rpc_error)

    def get_my_offer(self, offer_id):
        try:
            response = self.offers_stub.GetMyOffer.with_call(
                bisq_messages.GetMyOfferRequest(id=offer_id),
                metadata=[('password', self.api_password)])
            return response[0].offer
        except grpc.RpcError as rpc_error:
            print('gRPC API Exception: %s', rpc_error)

    def get_available_offers(self, direction, currency_code) -> list:
        if currency_code == 'BSQ':
            return self.get_available_bsqswap_offers(direction)
        else:
            return self.get_available_v1_offers(direction, currency_code)

    def get_available_v1_offers(self, direction, currency_code) -> list:
        try:
            response = self.offers_stub.GetOffers.with_call(
                bisq_messages.GetOffersRequest(direction=direction,
                                               currency_code=currency_code),
                metadata=[('password', self.api_password)])
            return list(response[0].offers)
        except grpc.RpcError as rpc_error:
            print('gRPC API Exception: %s', rpc_error)

    def get_available_bsqswap_offers(self, direction) -> list:
        try:
            response = self.offers_stub.GetBsqSwapOffers.with_call(
                bisq_messages.GetBsqSwapOffersRequest(direction=direction),
                metadata=[('password', self.api_password)])
            return list(response[0].bsq_swap_offers)
        except grpc.RpcError as rpc_error:
            print('gRPC API Exception: %s', rpc_error)

    def get_my_open_offers(self, direction, currency_code) -> list:
        if currency_code == 'BSQ':
            return self.get_my_bsqswap_offers(direction)
        else:
            return self.get_my_offers(direction, currency_code)

    def get_my_bsqswap_offers(self, direction) -> list:
        try:
            response = self.offers_stub.GetMyBsqSwapOffers.with_call(
                bisq_messages.GetBsqSwapOffersRequest(direction=direction),
                metadata=[('password', self.api_password)])
            return list(response[0].bsq_swap_offers)
        except grpc.RpcError as rpc_error:
            print('gRPC API Exception: %s', rpc_error)

    def get_my_offers(self, direction, currency_code) -> list:
        try:
            response = self.offers_stub.GetMyOffers.with_call(
                bisq_messages.GetMyOffersRequest(direction=direction,
                                                 currency_code=currency_code),
                metadata=[('password', self.api_password)])
            return list(response[0].offers)
        except grpc.RpcError as rpc_error:
            print('gRPC API Exception: %s', rpc_error)

    def get_trades(self, category) -> list:
        if category.casefold() == str(bisq_messages.GetTradesRequest.Category.CLOSED).casefold():
            return self.get_closed_trades()
        elif category.casefold() == str(bisq_messages.GetTradesRequest.Category.FAILED).casefold():
            return self.get_failed_trades()
        elif category.casefold() == str(bisq_messages.GetTradesRequest.Category.OPEN).casefold():
            return self.get_open_trades()
        else:
            raise Exception('Invalid trade category {0}, must be one of CLOSED | FAILED | OPEN'.format(category))

    def get_closed_trades(self) -> list:
        try:
            response = self.trades_stub.GetTrades.with_call(
                bisq_messages.GetTradesRequest(
                    category=bisq_messages.GetTradesRequest.Category.CLOSED),
                metadata=[('password', self.api_password)])
            return list(response[0].trades)
        except grpc.RpcError as rpc_error:
            print('gRPC API Exception: %s', rpc_error)

    def get_failed_trades(self) -> list:
        try:
            response = self.trades_stub.GetTrades.with_call(
                bisq_messages.GetTradesRequest(
                    category=bisq_messages.GetTradesRequest.Category.FAILED),
                metadata=[('password', self.api_password)])
            return list(response[0].trades)
        except grpc.RpcError as rpc_error:
            print('gRPC API Exception: %s', rpc_error)

    def get_open_trades(self) -> list:
        try:
            response = self.trades_stub.GetTrades.with_call(
                bisq_messages.GetTradesRequest(
                    category=bisq_messages.GetTradesRequest.Category.OPEN),
                metadata=[('password', self.api_password)])
            return list(response[0].trades)
        except grpc.RpcError as rpc_error:
            print('gRPC API Exception: %s', rpc_error)

    def get_open_trade(self, trade_id):
        open_trades = self.get_open_trades()
        if len(open_trades):
            trade_filter = filter(lambda candidate_trade: (candidate_trade.trade_id == trade_id), open_trades)
            filtered_trades = list(trade_filter)
            if len(filtered_trades):
                return filtered_trades[0]
            else:
                return None
        else:
            return None

    def get_open_fiat_trades(self, currency_code, direction) -> list:
        open_trades = self.get_open_trades()
        if len(open_trades):
            trade_filter = filter(lambda candidate_trade:
                                  (candidate_trade.offer.counter_currency_code == currency_code
                                   and candidate_trade.offer.direction == direction),
                                  open_trades)
            filtered_trades = list(trade_filter)
            if len(filtered_trades):
                # This sort is done on server !?
                filtered_trades.sort(key=operator.attrgetter('date'))
                return filtered_trades
            else:
                return []
        else:
            return []

    def get_oldest_open_fiat_trade(self, currency_code, direction):
        open_trades = self.get_open_trades()
        if len(open_trades):
            trade_filter = filter(lambda candidate_trade:
                                  (candidate_trade.offer.counter_currency_code == currency_code
                                   and candidate_trade.offer.direction == direction),
                                  open_trades)
            filtered_trades = list(trade_filter)
            if len(filtered_trades):
                # This sort is done on server !?
                filtered_trades.sort(key=operator.attrgetter('date'))
                return filtered_trades[0]
            else:
                return None
        else:
            return None

    def get_version(self):
        try:
            response = self.version_stub.GetVersion.with_call(
                bisq_messages.GetVersionRequest(),
                metadata=[('password', self.api_password)])
            return response[0].version
        except grpc.RpcError as rpc_error:
            print('gRPC API Exception: %s', rpc_error)

    @staticmethod
    def reference_price_offset(reference_price, spread_midpoint):
        return round(reference_price * spread_midpoint, 8)

    @staticmethod
    def reference_price_margin_offset(spread):
        return round(spread / Decimal(2.00), 2)

    def calc_buy_offset_price_margin(self, reference_price_margin, target_spread):
        offset = self.reference_price_margin_offset(target_spread)
        return round(reference_price_margin - offset, 2)

    def calc_sell_offset_price_margin(self, reference_price_margin, target_spread):
        offset = self.reference_price_margin_offset(target_spread)
        return round(reference_price_margin + offset, 2)

    @staticmethod
    def satoshis_to_btc_str(sats):
        btc = Decimal(round(sats * 0.00000001, 8))
        return format(btc, '2.8f')

    @staticmethod
    def get_my_offers_header():
        return '\t\t{0:<50} {1:<11} {2:<9} {3:>10} {4:>16} {5:>18} {6:<50}' \
            .format('OFFER_ID',
                    'DIRECTION',
                    'CURRENCY',
                    'PRICE',
                    'AMOUNT (BTC)',
                    'BUYER COST (EUR)',
                    'PAYMENT_ACCOUNT_ID')

    def get_offer_tbl(self, offer):
        header = self.get_my_offers_header()
        columns = '\t\t{0:<50} {1:<11} {2:<9} {3:>10} {4:>16} {5:>18} {6:<50}' \
            .format(offer.id,
                    offer.direction,
                    offer.counter_currency_code,
                    offer.price,
                    self.satoshis_to_btc_str(offer.amount),
                    offer.volume,
                    offer.payment_account_id)
        return header + '\n' + columns

    @staticmethod
    def get_my_bsqswap_offers_header():
        return '\t\t{0:<50} {1:<11} {2:<10} {3:>11} {4:>12} {5:>16}' \
            .format('OFFER_ID',
                    'DIRECTION',
                    'CURRENCY',
                    'PRICE (BTC)',
                    'AMOUNT (BTC)',
                    'BUYER COST (BSQ)')

    def get_bsqswap_offer_tbl(self, offer):
        btc_amount = round(offer.amount * Decimal(0.00000001), 8)
        headers = self.get_my_bsqswap_offers_header()
        columns = '\t\t{0:<50} {1:<11} {2:<10} {3:>11} {4:>12} {5:>16}' \
            .format(offer.id,
                    offer.direction + ' (BTC)',
                    offer.base_currency_code,
                    offer.price,
                    btc_amount,
                    offer.volume)
        return headers + '\n' + columns

    def get_my_usd_offers_tbl(self):
        headers = self.get_my_offers_header()
        rows = []
        my_usd_offers = self.get_my_offers('BUY', 'USD')
        for o in my_usd_offers:
            columns = '\t\t{0:<50} {1:<12} {2:<12} {3:>12}'.format(o.id, o.direction, o.counter_currency_code, o.price)
            rows.append(columns)
        my_usd_offers = self.get_my_offers('SELL', 'USD')
        for o in my_usd_offers:
            columns = '\t\t{0:<50} {1:<12} {2:<12} {3:>12}'.format(o.id, o.direction, o.counter_currency_code, o.price)
            rows.append(columns)
        return self.get_tbl(headers, rows)

    @staticmethod
    def get_trades_header():
        return '\t\t{0:<50} {1:<26} {2:<20} {3:>16} {4:>13} {5:>12}' \
            .format('TRADE_ID',
                    'DATE',
                    'ROLE',
                    'PRICE',
                    'AMOUNT (BTC)',
                    'BUYER COST')

    def get_trades_tbl(self, trades):
        headers = self.get_trades_header()
        rows = []
        for trade in trades:
            # For fiat offer the baseCurrencyCode is BTC and the counterCurrencyCode is the fiat currency.
            # For altcoin offers it is the opposite: baseCurrencyCode is the altcoin and the counterCurrencyCode is BTC.
            if trade.offer.base_currency_code == 'BTC':
                currency_code = trade.offer.counter_currency_code
            else:
                currency_code = trade.offer.base_currency_code
            columns = '\t\t{0:<50} {1:<26} {2:<20} {3:>16} {4:>13} {5:>12}' \
                .format(trade.trade_id,
                        datetime.fromtimestamp(
                            trade.date / Decimal(1000.0),
                            tz=timezone.utc).isoformat(),
                        trade.role,
                        trade.trade_price + ' ' + currency_code,
                        self.satoshis_to_btc_str(trade.trade_amount_as_long),
                        trade.trade_volume + ' ' + currency_code)
            rows.append(columns)
        return self.get_tbl(headers, rows)

    @staticmethod
    def get_tbl(headers, rows):
        tbl = headers + '\n'
        for row in rows:
            tbl = tbl + row + '\n'
        return tbl

    # Return a multi-line string describing a trade + payment details.
    def get_trade_payment_summary(self, trade):
        currency_code = trade.offer.counter_currency_code
        is_my_offer = trade.offer.is_my_offer
        if is_my_offer is True:
            payment_details = trade.contract.taker_payment_account_payload.payment_details
        else:
            payment_details = trade.contract.maker_payment_account_payload.payment_details

        return 'ID: {0}\nDate: {1}\nRole: {2}\nPrice: {3} {4}\nAmount: {5} BTC\nBuyer cost: {6} {7}\nPayment {8}' \
            .format(trade.trade_id,
                    datetime.fromtimestamp(
                        trade.date / Decimal(1000.0),
                        tz=timezone.utc).isoformat(),
                    trade.role,
                    trade.trade_price,
                    currency_code,
                    self.satoshis_to_btc_str(
                        trade.trade_amount_as_long),
                    trade.trade_volume,
                    currency_code,
                    payment_details)

    def __str__(self):
        return 'host=' + self.host + ' port=' + self.port + ' api_password=' + '*****'
