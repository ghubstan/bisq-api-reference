import sys
import threading
import time

from bisq.bots.bisq_events.trade_event import TradeEvent
from bisq.bots.bisq_events.trade_event_data import TradeEventData
from bisq_client import BisqClient
from protocol_step import *

MAX_POLLING_ITERATIONS = 1000


# noinspection PyInitNewSignature
class SepaMMBot(BisqClient):
    def __init__(self, host, port, api_password, max_polling_iterations, event_dispatcher):
        super().__init__(host, port, api_password)
        self.is_running = False
        self.timer = None
        self.max_polling_iterations = max_polling_iterations
        self.event_dispatcher = event_dispatcher
        log.info('Initialized ' + str(self))
        log.info('Click the Start button to make SEPA offers and trade.')

    def start(self, offers_poll_interval_in_sec,
              reference_price_margin,
              target_spread,
              amount_in_satoshis,
              sepa_payment_account_id):
        log.info('Starting SEPA market maker bot.')
        log.info('Reference Price Margin = %.2f%s, Spread = %.2f%s, Amount = %d satoshis, SEPA Payment Account Id = %s',
                 reference_price_margin, '%', target_spread, '%', amount_in_satoshis, sepa_payment_account_id)

        if self.is_connected() is False:
            self.open_channel()

        # Poll the bot's offers.  Replace taken offers with new offers so there is always 1 buy and 1 sell offer.
        self.timer = threading.Timer(0, ())
        count = 0
        interval = 0
        try:
            self.is_running = True
            while not self.timer.finished.wait(interval):
                # Make sure there is 1 SEPA BUY BTC offer, and 1 SEPA SELL BTC offer.
                self.make_market(reference_price_margin,
                                 target_spread,
                                 amount_in_satoshis,
                                 sepa_payment_account_id)
                time.sleep(1)
                # Execute a single trade protocol step for each open SEPA trade.
                self.process_open_trades(sepa_payment_account_id)

                all_closed_trades = self.get_closed_trades()
                log.info('My Closed Trades:\n%s', self.get_trades_tbl(all_closed_trades))
                log.info('Bot is sleeping for %d seconds.', offers_poll_interval_in_sec)
                count = count + 1
                if count >= self.max_polling_iterations:
                    self.timer.cancel()
                else:
                    interval = offers_poll_interval_in_sec
            sys.exit(0)
        except KeyboardInterrupt:
            log.info('Timer interrupted')
            sys.exit(0)

    def shutdown(self):
        log.info('Shutting down SEPA market maker bot.')
        if self.timer is not None:
            self.timer.cancel()
        self.close_channel()
        self.is_running = False

    def make_market(self, reference_price_margin,
                    target_spread,
                    amount_in_satoshis,
                    sepa_payment_account_id):
        self.make_buy_offer(reference_price_margin, target_spread, amount_in_satoshis, sepa_payment_account_id)
        self.make_sell_offer(reference_price_margin, target_spread, amount_in_satoshis, sepa_payment_account_id)

    def process_open_trades(self, sepa_payment_account_id):
        self.process_buy_trades(sepa_payment_account_id)
        self.process_sell_trades(sepa_payment_account_id)

    # Perform the next automatic trade protocol step for each open BUY trade.
    # Manual steps trigger an event for the UI.
    def process_buy_trades(self, sepa_payment_account_id):
        open_trades = self.get_open_fiat_trades('EUR', 'BUY')
        trade_filter = filter(
            lambda candidate_trade: (candidate_trade.offer.payment_account_id == sepa_payment_account_id),
            open_trades)
        filtered_trades = list(trade_filter)
        if len(filtered_trades):
            log.info('Do one protocol step for %d open BUY trades.', len(filtered_trades))
            for trade in filtered_trades:
                protocol_step = ProtocolStep(self.trades_stub, self.api_password, trade.trade_id)
                if protocol_step.can_execute() is True:
                    protocol_step.run()
                else:
                    log.warn('Next protocol step is manual (trade %s).', trade.trade_id)
                    next_buy_step = protocol_step.get_next_buy_step(trade)
                    if next_buy_step == SEND_PAYMENT_STARTED_MSG:
                        buy_trade_tbl = self.get_trades_tbl([trade])
                        log.warn('Dispatching %s for trade:\n%s',
                                 TradeEvent.MUST_SEND_PAYMENT_STARTED_MSG_EVENT,
                                 buy_trade_tbl)
                        self.event_dispatcher.dispatch_event(
                            TradeEvent(TradeEvent.MUST_SEND_PAYMENT_STARTED_MSG_EVENT,
                                       TradeEventData(trade, 'Send trade payment and click the button.')))
        else:
            log.info('There are no open BUY trades at this time.')

    # Perform the next automatic trade protocol step for each open SELL trade.
    # Manual steps trigger an event for the UI.
    def process_sell_trades(self, sepa_payment_account_id):
        open_trades = self.get_open_fiat_trades('EUR', 'SELL')
        trade_filter = filter(
            lambda candidate_trade: (candidate_trade.offer.payment_account_id == sepa_payment_account_id),
            open_trades)
        filtered_trades = list(trade_filter)
        if len(filtered_trades):
            log.info('Do one protocol step for %d open SELL trades.', len(filtered_trades))
            for trade in filtered_trades:
                protocol_step = ProtocolStep(self.trades_stub, self.api_password, trade.trade_id)
                if protocol_step.can_execute() is True:
                    protocol_step.run()
                else:
                    log.warn('Next protocol step is manual (trade %s).', trade.trade_id)
                    next_sell_step = protocol_step.get_next_sell_step(trade)
                    if next_sell_step == SEND_PAYMENT_RECEIVED_MSG:
                        sell_trade_tbl = self.get_trades_tbl([trade])
                        log.warn('Dispatching %s for trade:\n%s',
                                 TradeEvent.MUST_SEND_PAYMENT_RECEIVED_MSG_EVENT,
                                 sell_trade_tbl)
                        self.event_dispatcher.dispatch_event(
                            TradeEvent(TradeEvent.MUST_SEND_PAYMENT_RECEIVED_MSG_EVENT,
                                       TradeEventData(
                                           trade,
                                           'Confirm trade payment was received and click the button.')))
        else:
            log.info('There are no open SELL trades at this time.')

    def make_buy_offer(self, reference_price_margin,
                       target_spread,
                       amount_in_satoshis,
                       sepa_payment_account_id):
        # Get or create buy offer.
        buy_offer = self.get_my_buy_btc_offer()
        if buy_offer is None:
            log.info('No buy BTC offers.')
            buy_offer = self.create_buy_btc_offer(reference_price_margin,
                                                  target_spread,
                                                  amount_in_satoshis,
                                                  sepa_payment_account_id)
            log.info('Created new BUY BTC offer:\n%s', self.get_offer_tbl(buy_offer))
        else:
            log.info('My open BUY BTC offer:\n%s', self.get_offer_tbl(buy_offer))

    def make_sell_offer(self, reference_price_margin,
                        target_spread,
                        amount_in_satoshis,
                        sepa_payment_account_id):
        # Get or create sell offer.
        sell_offer = self.get_my_sell_btc_offer()
        if sell_offer is None:
            log.info('No sell BTC offers.')
            sell_offer = self.create_sell_btc_offer(reference_price_margin,
                                                    target_spread,
                                                    amount_in_satoshis,
                                                    sepa_payment_account_id)
            log.info('Created new SELL BTC offer:\n%s', self.get_offer_tbl(sell_offer))
        else:
            log.info('My open SELL BTC offer:\n%s', self.get_offer_tbl(sell_offer))

    def create_buy_btc_offer(self, reference_price_margin,
                             target_spread,
                             amount_in_satoshis,
                             sepa_payment_account_id):
        margin = self.calc_buy_offset_price_margin(reference_price_margin, target_spread)
        return self.create_margin_priced_offer('EUR',
                                               'BUY',
                                               margin,
                                               amount_in_satoshis,
                                               amount_in_satoshis,
                                               sepa_payment_account_id)

    def create_sell_btc_offer(self, reference_price_margin,
                              target_spread,
                              amount_in_satoshis,
                              sepa_payment_account_id):
        margin = self.calc_sell_offset_price_margin(reference_price_margin, target_spread)
        return self.create_margin_priced_offer('EUR',
                                               'SELL',
                                               margin,
                                               amount_in_satoshis,
                                               amount_in_satoshis,
                                               sepa_payment_account_id)

    def get_my_buy_btc_offer(self):
        my_eur_buy_offers = self.get_my_offers('BUY', 'EUR')
        if my_eur_buy_offers is None:
            return None
        else:
            offer_filter = filter(lambda candidate_offer:
                                  (candidate_offer.payment_method_id == 'SEPA'),
                                  my_eur_buy_offers)
            filtered_offers = list(offer_filter)
            if len(filtered_offers):
                return filtered_offers[0]
            else:
                return None

    def get_my_sell_btc_offer(self):
        my_eur_sell_offers = self.get_my_offers('SELL', 'EUR')
        if len(my_eur_sell_offers):
            offer_filter = filter(lambda candidate_offer:
                                  (candidate_offer.payment_method_id == 'SEPA'),
                                  my_eur_sell_offers)
            filtered_offers = list(offer_filter)
            if len(filtered_offers):
                return filtered_offers[0]
            else:
                return None
        else:
            return None

    def send_payment_started_msg(self, trade_id):
        protocol_step = ProtocolStep(self.trades_stub, self.api_password, trade_id)
        protocol_step.send_payment_started_msg()

    def send_payment_received_msg(self, trade_id):
        protocol_step = ProtocolStep(self.trades_stub, self.api_password, trade_id)
        protocol_step.send_payment_received_msg()

    def __str__(self):
        description = 'SepaMMBot: host={0}, port={1}, api_password={2}' \
            .format(self.host,
                    str(self.port),
                    '*****')
        return description


def main(host, port, api_password):
    SepaMMBot(host, port, api_password).start()
