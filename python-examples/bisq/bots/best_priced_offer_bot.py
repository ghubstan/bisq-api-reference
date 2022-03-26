import argparse
import configparser
import sys
import threading
import time
from decimal import Decimal

from bisq_client import BisqClient
from logger import log


# To run file in Python console:  main('localhost', 9998, 'xyz')
# To run from another Python script:
#   import best_priced_offer_bot
#   best_priced_offer_bot.main('localhost', 9998, 'xyz')


# noinspection PyInitNewSignature
class BestPricedOfferBot(BisqClient):
    def __init__(self, host, port, api_password):
        super().__init__(host, port, api_password)
        self.config = configparser.ConfigParser()
        self.config.read('best_priced_offer_bot.ini')
        self.my_synced_offer_ids = [offer_id.strip() for offer_id in self.config.get('offers', 'offer_ids').split(',')]
        if not self.my_synced_offer_ids[0]:
            sys.exit('best_priced_offer_bot.ini\'s offer_ids value is not specified')

    def run(self):
        # Before checking available offers for their best price, update my offers with my own most competitive price.
        self.update_my_offers_with_my_most_competitive_price()

        # Poll available offers fpr their best price, and adjust my own offer prices to compete.
        timer = threading.Timer(0, ())
        max_iterations = 100
        count = 0
        interval = 0
        try:
            while not timer.finished.wait(interval):
                self.check_best_available_price_and_update()
                count = count + 1
                if count >= max_iterations:
                    timer.cancel()
                else:
                    interval = self.check_price_interval_in_sec()
            sys.exit(0)
        except KeyboardInterrupt:
            log.warning('Timer interrupted')
            sys.exit(0)

    def update_my_offers_with_my_most_competitive_price(self):
        log.info('Updating or recreating my synced %s %s offers with my most competitive price.',
                 self.offer_type_direction(), self.offer_type_currency())
        # Sort all my open offers by price to find the most competitive price.
        my_offers = self.get_my_open_offers(self.offer_type_direction(), self.offer_type_currency())
        my_offers.sort(key=lambda x: x.price, reverse=self.offer_type_direction() == 'SELL')
        if len(my_offers):
            my_most_competitive_price = Decimal(my_offers[0].price)
        else:
            sys.exit('I have no {} {} offers to sync.'
                     .format(self.offer_type_direction(), self.offer_type_currency()))

        # Update (or recreate) any synced offers that do not have my most competitive price.
        offer_filter = filter(lambda candidate_offer: (candidate_offer.id in self.my_synced_offer_ids), my_offers)
        filtered_offers = list(offer_filter)
        for offer in filtered_offers:
            if Decimal(offer.price) != my_most_competitive_price:
                self.update_my_offer(offer, my_most_competitive_price)
            else:
                log.info('My synced %s %s offer %s already has my most competitive price (%s)',
                         self.offer_type_direction(), self.offer_type_currency(), offer.id, offer.price)

    def update_my_offer(self, my_offer, competitive_price):
        if my_offer.is_bsq_swap_offer:
            new_offer = self.recreate_bsqswap_offer(my_offer, competitive_price)
            log.info('Replaced old %s %s offer %s with new offer %s with fixed-price %s.',
                     self.offer_type_direction(),
                     self.offer_type_currency(),
                     my_offer.id,
                     new_offer.id,
                     new_offer.price)
        else:
            self.update_offer_fixed_price(my_offer, competitive_price)

            try:
                time.sleep(5)  # Wait for offer to be re-published with new fixed-price.
            except KeyboardInterrupt:
                log.warning('Interrupted while updating offer.')
                sys.exit(0)

            updated_offer = self.get_my_offer(my_offer.id)
            log.info('Updated %s %s offer %s with new fixed-price %s.',
                     self.offer_type_direction(),
                     self.offer_type_currency(),
                     updated_offer.id,
                     updated_offer.price)

    def recreate_bsqswap_offer(self, old_offer, competitive_price):
        # Remove my_offer from offer book.
        self.cancel_offer(old_offer.id)
        # Get canceled offer's details for new offer.
        old_offer_direction = self.offer_type_direction()
        old_offer_amount = old_offer.amount
        old_offer_min_amount = old_offer.min_amount
        competitive_price_str = str(competitive_price)
        new_offer = self.create_bsqswap_offer(old_offer_direction,
                                              competitive_price_str,
                                              old_offer_amount,
                                              old_offer_min_amount)
        self.replace_synced_offer_id(old_offer.id, new_offer.id)
        return new_offer

    def update_offer_fixed_price(self, my_offer, competitive_price):
        competitive_price_str = str(competitive_price)
        self.edit_offer_fixed_price(my_offer, competitive_price_str)
        return

    def replace_synced_offer_id(self, canceled_offer_id, new_offer_id):
        # Remove my_offer.id from list of my_synced_offer_ids.
        self.my_synced_offer_ids.remove(canceled_offer_id)
        # Add new_offer.id to list of my_synced_offer_ids.
        self.my_synced_offer_ids.append(new_offer_id)

    def get_best_available_price_for_acceptable_amount(self):
        available_offers = self.get_available_offers(self.offer_type_direction(), self.offer_type_currency())
        if len(available_offers) == 0:
            log.info('No available offers found.')
            return None

        # Filter all available offers that are below 'min_accepted_offer_amount_for_price_adaption'.
        min_amount = self.safeguard_min_accepted_amount()
        offer_filter = filter(lambda offer: (Decimal(offer.volume) >= Decimal(min_amount)), available_offers)
        filtered_offers = list(offer_filter)
        # Sort the filtered, available offers by price.
        filtered_offers.sort(key=lambda x: x.price, reverse=self.offer_type_direction() == 'SELL')

        # Find the best (most competitive) available offer price.
        if len(filtered_offers):
            current_best_price = Decimal(filtered_offers[0].price)
            if self.offer_type_direction() == 'BUY':
                if self.is_price_below_accepted_limit(current_best_price):
                    log.info('Best available price %s is too low, below safeguard_max_accepted_price %s.',
                             current_best_price, self.safeguard_min_accepted_price())
                    return self.safeguard_min_accepted_price()
                else:
                    log.info('Best available price is max of current_best_price, min_accepted_price (%s, %s)',
                             current_best_price, self.safeguard_min_accepted_price())
                    return max(current_best_price, self.safeguard_min_accepted_price())
            else:
                if self.is_price_above_accepted_limit(current_best_price):
                    log.info('Best available price %s is too high, above safeguard_max_accepted_price %s.',
                             current_best_price, self.safeguard_max_accepted_price())
                    return self.safeguard_max_accepted_price()
                else:
                    log.info('Best available price is min of current_best_price, max_accepted_price (%s, %s)',
                             current_best_price, self.safeguard_max_accepted_price())
                    return min(current_best_price, self.safeguard_max_accepted_price())

    def is_price_below_accepted_limit(self, price):
        return price < self.safeguard_min_accepted_price()

    def is_price_above_accepted_limit(self, price):
        return price > self.safeguard_max_accepted_price()

    def check_best_available_price_and_update(self):
        log.info('Polling available offers for the best price...')
        best_available_price = self.get_best_available_price_for_acceptable_amount()
        if best_available_price is None:
            log.warning('Could not find best available price.')
            return

        for offer_id in self.my_synced_offer_ids:
            offer = super().get_my_offer(offer_id)
            if offer is None:
                err_msg = 'You do not have an offer with id {}.\n'.format(offer_id)
                err_msg += 'The offer may have been taken or canceled.\n'
                err_msg += 'Update your best_priced_offer_bot.ini file and restart the bot.'
                sys.exit(err_msg)

            if Decimal(offer.price) != best_available_price:
                log.info('Update %s %s offer %s price (%s) with a competitive price (%s).',
                         self.offer_type_direction(),
                         self.offer_type_currency(),
                         offer.id,
                         offer.price,
                         best_available_price)
                self.update_my_offer(offer, best_available_price)
            else:
                log.info('My %s offer %s (with price %s) already has the most competitive price (%s).',
                         offer.direction, offer.id, offer.price, best_available_price)

    def check_price_interval_in_sec(self):
        return int(self.config.get('general', 'check_price_interval_in_sec'))

    def offer_type_currency(self):
        return self.config.get('offer type', 'currency')

    def offer_type_direction(self):
        return self.config.get('offer type', 'direction')

    def safeguard_min_accepted_amount(self):
        return int(self.config.get('safeguards', 'min_accepted_offer_amount_for_price_adaption'))

    def safeguard_min_accepted_price(self):
        return Decimal(self.config.get('safeguards', 'min_accepted_price'))

    def safeguard_max_accepted_price(self):
        return Decimal(self.config.get('safeguards', 'max_accepted_price'))

    def __str__(self):
        return 'BestPricedOfferBot: ' + 'host=' + self.host + ', port=' + str(self.port) + ', api_password=' + '*****'


def parse_args(sysargv):
    parser = argparse.ArgumentParser()
    parser.add_argument('host', help='API daemon hostname or IP address')
    parser.add_argument('port', type=int, help='API daemon listening port')
    parser.add_argument('api_password', help='API password')
    return parser.parse_args(sysargv)


def main():
    args = parse_args(sys.argv[1:])
    BestPricedOfferBot(args.host, args.port, args.api_password).run()


if __name__ == '__main__':
    main()
