# From https://expobrain.net/2010/07/31/simple-event-dispatcher-in-python
from bots.logger import log
from trade_event import TradeEvent


class TradeEventListener(object):
    """
    First class which ask who is listening to it
    """

    def __init__(self, event_dispatcher):
        # Save a reference to the event dispatch
        self.event_dispatcher = event_dispatcher

        # Listen for the MUST_SEND_PAYMENT_STARTED_MSG_EVENT event type
        self.event_dispatcher.add_event_listener(TradeEvent.MUST_SEND_PAYMENT_STARTED_MSG_EVENT,
                                                 self.handle_trade_event)

    def handle_trade_event(self, event):
        """
        Event handler for the MUST_SEND_PAYMENT_STARTED_MSG_EVENT event type
        """
        log.info('Handling %s: %s', event.type, event.data)
        data = event.data
        log.info('trade:  %s', str(data.trade))
        log.info('what_to_do:  %s', data.instructions)
