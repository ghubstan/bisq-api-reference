import grpc

import python_examples.bisqapi.grpc_pb2 as bisq_messages
from logger import log

WAIT_FOR_TRADE_DEPOSIT_CONFIRMATION = 'WAIT_FOR_TRADE_DEPOSIT_CONFIRMATION'
WAIT_FOR_PAYMENT_STARTED_MSG = 'WAIT_FOR_PAYMENT_STARTED_MSG'
WAIT_FOR_PAYMENT_RECEIVED_MSG = 'WAIT_FOR_PAYMENT_RECEIVED_MSG'
WAIT_FOR_PAYOUT_IS_PUBLISHED = 'WAIT_FOR_PAYOUT_IS_PUBLISHED'

SEND_PAYMENT_STARTED_MSG = 'SEND_PAYMENT_STARTED_MSG'
SEND_PAYMENT_RECEIVED_MSG = 'SEND_PAYMENT_RECEIVED_MSG'
CLOSE_TRADE = 'CLOSE_TRADE'
STOP_BOT_OPEN_UI_CONTACT_SUPPORT = 'STOP_BOT_OPEN_UI_CONTACT_SUPPORT'


class ProtocolStep():
    def __init__(self, trades_stub, api_password, trade_id):
        self.trades_stub = trades_stub
        self.api_password = api_password
        self.trade_id = trade_id
        self.timer = None

    def run(self):
        log.info('Execute automatic protocol step for trade %s.', self.trade_id)
        trade = self.get_trade()
        if self.i_am_buyer(trade) is True:
            self.do_next_buy_step(trade)
        else:
            self.do_next_sell_step(trade)

    def can_execute(self):
        trade = self.get_trade()
        if self.i_am_buyer(trade) is True:
            next_buy_step = self.get_next_buy_step(trade)
            return next_buy_step != SEND_PAYMENT_STARTED_MSG
        else:
            next_sell_step = self.get_next_sell_step(trade)
            return next_sell_step != SEND_PAYMENT_RECEIVED_MSG

    def do_next_buy_step(self, trade):
        next_buy_step = self.get_next_buy_step(trade)
        log.info('\tTrade %s: next buy step: %s', trade.trade_id, next_buy_step)
        if next_buy_step == SEND_PAYMENT_STARTED_MSG:
            log.warn('\tPayment must be sent manually, and payment sent msg must be sent from UI.')
        elif next_buy_step == WAIT_FOR_PAYMENT_RECEIVED_MSG:
            log.info('\tPayment sent, waiting for payment received msg ...')
        elif next_buy_step == WAIT_FOR_PAYOUT_IS_PUBLISHED:
            log.info('\tPayment received, waiting for payout tx to be published ...')
        elif next_buy_step == CLOSE_TRADE:
            log.info('\tPayment received, payout tx is published, closing trade ...')
            self.close_trade()
        elif next_buy_step == STOP_BOT_OPEN_UI_CONTACT_SUPPORT:
            log.error('Something bad happened.  You have to shutdown the bot,'
                      + ' start the desktop UI, and open a support ticked for trade %s',
                      self.trade_id)

    def do_next_sell_step(self, trade):
        next_sell_step = self.get_next_sell_step(trade)
        log.info('\tTrade %s: next sell step: %s', trade.trade_id, next_sell_step)
        if next_sell_step == WAIT_FOR_PAYMENT_STARTED_MSG:
            log.info('\tWaiting for buyer to start payment ...')
        if next_sell_step == SEND_PAYMENT_RECEIVED_MSG:
            log.warn('\tPayment receipt must be confirmed manually, and payment received'
                     + ' confirmation msg must be sent from UI.')
        elif next_sell_step == WAIT_FOR_PAYOUT_IS_PUBLISHED:
            log.info('\tPayment received, waiting for payout tx to be published ...')
        elif next_sell_step == CLOSE_TRADE:
            log.info('\tPayment received, payout tx is published, closing trade ...')
            self.close_trade()
        elif next_sell_step == STOP_BOT_OPEN_UI_CONTACT_SUPPORT:
            log.error('Something bad happened.  You have to shutdown the bot,'
                      + ' start the desktop UI, and open a support ticked for trade %s',
                      self.trade_id)

    @staticmethod
    def get_next_buy_step(trade):
        if trade.is_deposit_published is False:
            return WAIT_FOR_TRADE_DEPOSIT_CONFIRMATION
        elif trade.is_deposit_confirmed is False:
            return WAIT_FOR_TRADE_DEPOSIT_CONFIRMATION
        elif trade.is_payment_started_message_sent is False:
            return SEND_PAYMENT_STARTED_MSG
        elif trade.is_payment_started_message_sent is True and trade.is_payment_received_message_sent is False:
            return WAIT_FOR_PAYMENT_RECEIVED_MSG
        elif trade.is_payment_received_message_sent is True and trade.is_payout_published is False:
            return WAIT_FOR_PAYOUT_IS_PUBLISHED
        elif trade.is_payout_published is True:
            return CLOSE_TRADE
        else:
            return STOP_BOT_OPEN_UI_CONTACT_SUPPORT

    @staticmethod
    def get_next_sell_step(trade):
        if trade.is_deposit_published is False:
            return WAIT_FOR_TRADE_DEPOSIT_CONFIRMATION
        elif trade.is_deposit_confirmed is False:
            return WAIT_FOR_TRADE_DEPOSIT_CONFIRMATION
        elif trade.is_payment_started_message_sent is False:
            return WAIT_FOR_PAYMENT_STARTED_MSG
        elif trade.is_payment_started_message_sent is True and trade.is_payment_received_message_sent is False:
            return SEND_PAYMENT_RECEIVED_MSG
        elif trade.is_payment_received_message_sent is True and trade.is_payout_published is False:
            return WAIT_FOR_PAYOUT_IS_PUBLISHED
        elif trade.is_payout_published is True:
            return CLOSE_TRADE
        else:
            return STOP_BOT_OPEN_UI_CONTACT_SUPPORT

    @staticmethod
    def i_am_buyer(trade):
        offer = trade.offer
        if offer.is_my_offer is True:
            return offer.direction == 'BUY'
        else:
            return offer.direction == 'SELL'

    def send_payment_started_msg(self):
        trade = self.get_trade()
        next_buy_step = self.get_next_buy_step(trade)
        if next_buy_step != SEND_PAYMENT_STARTED_MSG:
            raise 'Trade {0} is not in proper state to send a payment started msg.' \
                  + '  Next step should be {1}.'.format(trade.trade_id, next_buy_step)
        log.info('Sending payment started msg for trade %s.', self.trade_id)
        try:
            self.trades_stub.ConfirmPaymentStarted.with_call(
                bisq_messages.ConfirmPaymentStartedRequest(trade_id=self.trade_id),
                metadata=[('password', self.api_password)])

        except grpc.RpcError as rpc_error:
            print('gRPC API Exception: %s', rpc_error)

    def send_payment_received_msg(self):
        trade = self.get_trade()
        next_sell_step = self.get_next_sell_step(trade)
        if next_sell_step != SEND_PAYMENT_RECEIVED_MSG:
            raise 'Trade {0} is not in proper state to send a payment received confirmation msg.' \
                  + '  Next step should be {1}.'.format(trade.trade_id, next_sell_step)
        log.info('Sending payment received confirmation msg for trade %s.', self.trade_id)
        try:
            self.trades_stub.ConfirmPaymentReceived.with_call(
                bisq_messages.ConfirmPaymentReceivedRequest(trade_id=self.trade_id),
                metadata=[('password', self.api_password)])
        except grpc.RpcError as rpc_error:
            print('gRPC API Exception: %s', rpc_error)

    def close_trade(self):
        try:
            self.trades_stub.CloseTrade.with_call(
                bisq_messages.CloseTradeRequest(trade_id=self.trade_id),
                metadata=[('password', self.api_password)])
        except grpc.RpcError as rpc_error:
            print('gRPC API Exception: %s', rpc_error)

    def get_trade(self):
        try:
            response = self.trades_stub.GetTrade.with_call(
                bisq_messages.GetTradeRequest(trade_id=self.trade_id),
                metadata=[('password', self.api_password)])
            return response[0].trade
        except grpc.RpcError as rpc_error:
            print('gRPC API Exception: %s', rpc_error)

    def __str__(self):
        description = 'ProtocolStep: trades_stub={0}, trade_id={1}' \
            .format(self.trades_stub,
                    str(self.trade_id))
        return description
