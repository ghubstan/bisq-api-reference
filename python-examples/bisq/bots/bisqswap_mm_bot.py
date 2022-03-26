import argparse
import configparser
import sys
import threading
from decimal import *

from bisq_client import BisqClient
from logger import log


# To run file in Python console:  main('localhost', 9998, 'xyz')
# To run from another Python script:
#   import bisqswap_mm_bot
#   bisqswap_mm_bot.main('localhost', 9998, 'xyz')


# noinspection PyInitNewSignature
class BsqSWapMMBot(BisqClient):
    def __init__(self, host, port, api_password):
        super().__init__(host, port, api_password)
        self.config = configparser.ConfigParser()
        self.config.read('bisqswap_mm_bot.ini')
        log.info('Starting %s', str(self))

    def run(self):
        self.print_configuration()

        # Poll the bot's offers.  Replace taken offers with new offers so there is always 1 buy and 1 sell offer.
        timer = threading.Timer(0, ())
        max_iterations = 100
        count = 0
        interval = 0
        try:
            while not timer.finished.wait(interval):
                self.make_market()
                count = count + 1
                if count >= max_iterations:
                    timer.cancel()
                else:
                    interval = self.offers_poll_interval_in_sec()
            sys.exit(0)
        except KeyboardInterrupt:
            log.warning('Timer interrupted')
            sys.exit(0)

    def make_market(self):
        # Get or create mm buy offer.
        buy_offer = self.get_my_buy_btc_offer()
        if buy_offer is None:
            log.info('\tNo buy BTC with BSQ offers.')
            buy_offer = self.create_buy_btc_offer()
            log.info('My new BUY BTC with BSQ offer:\n%s', self.get_bsqswap_offer_tbl(buy_offer))
        else:
            log.info('My old BUY BTC with BSQ offer:\n%s', self.get_bsqswap_offer_tbl(buy_offer))

        # Get or create mm sell offer.
        sell_offer = self.get_my_sell_btc_offer()
        if sell_offer is None:
            sell_offer = self.create_sell_btc_offer()
            log.info('My new SELL BTC for BSQ offer:\n%s', self.get_bsqswap_offer_tbl(sell_offer))
        else:
            log.info('My old SELL BTC for BSQ offer:\n%s', self.get_bsqswap_offer_tbl(sell_offer))

        all_closed_trades = self.get_closed_trades()
        log.info('My Closed Trades:\n%s', self.get_trades_tbl(all_closed_trades))

        log.info('Going to sleep for %d seconds', self.offers_poll_interval_in_sec())

    def get_my_buy_btc_offer(self):
        my_buy_offers = self.get_my_bsqswap_offers('BUY')
        if len(my_buy_offers):
            return my_buy_offers[0]
        else:
            return None

    def create_buy_btc_offer(self):
        amount = self.amount()
        buy_price = self.calc_buy_offset_price()
        log.info('Creating BUY BTC offer with %.2f BSQ at price of %.8f BTC for 1 BSQ.', amount, buy_price)
        log.warning('Remember, a buy offer is an offer to buy BTC!')
        new_buy_offer = self.create_bsqswap_offer('BUY', str(buy_price), amount, amount)
        return new_buy_offer

    def get_my_sell_btc_offer(self):
        my_sell_offers = self.get_my_bsqswap_offers('SELL')
        if len(my_sell_offers):
            return my_sell_offers[0]
        else:
            return None

    def create_sell_btc_offer(self):
        amount = self.amount()
        sell_price = self.calc_sell_offset_price()
        log.info('Creating SELL BTC for %.2f BSQ at price %.8f BTC for 1 BSQ.', amount, sell_price)
        log.warning('Remember, a sell offer is an offer to sell BTC!')
        new_sell_offer = self.create_bsqswap_offer('SELL', str(sell_price), amount, amount)
        return new_sell_offer

    def calc_buy_offset_price(self):
        # With a 4% spread, a buy BTC offer price would be market price -2%.
        # 2% of X = 0.00000080
        offset = self.reference_price_offset(self.reference_price(), self.spread_midpoint())
        return round(self.reference_price() - offset, 8)

    def calc_sell_offset_price(self):
        # With a 4% spread, a sell BTC offer price would be market price +2%.
        offset = self.reference_price_offset(self.reference_price(), self.spread_midpoint())
        return round(self.reference_price() + offset, 8)

    def print_configuration(self):
        offset = self.reference_price_offset(self.reference_price(), self.spread_midpoint())
        description = 'Bisq Reference Price = {0} BTC  Spread = {1}%  Reference Price Offset={2:.8f} BTC' \
            .format(self.reference_price(),
                    self.spread(),
                    offset)
        log.info('Bot Configuration: %s', description)

    def offers_poll_interval_in_sec(self):
        return int(self.config.get('general', 'offers_poll_interval_in_sec'))

    def reference_price(self):
        return Decimal(self.config.get('general', 'reference_price'))

    def spread(self):
        return Decimal(self.config.get('general', 'spread'))

    def spread_midpoint(self):
        midpoint = self.spread() / Decimal(2)
        return Decimal(round(midpoint * Decimal(0.01), 2))

    def amount(self):
        return int(self.config.get('general', 'amount'))

    def __str__(self):
        description = 'BsqSWapMMBot: host={0}, port={1}, api_password={2}' \
            .format(self.host,
                    str(self.port),
                    '*****')
        return description


def parse_args(sysargv):
    parser = argparse.ArgumentParser()
    parser.add_argument('host', help='API daemon hostname or IP address')
    parser.add_argument('port', type=int, help='API daemon listening port')
    parser.add_argument('api_password', help='API password')
    return parser.parse_args(sysargv)


def main():
    args = parse_args(sys.argv[1:])
    BsqSWapMMBot(args.host, args.port, args.api_password).run()


if __name__ == '__main__':
    main()
