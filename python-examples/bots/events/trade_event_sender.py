# From https://expobrain.net/2010/07/31/simple-event-dispatcher-in-python
from trade_event import TradeEvent
from trade_event_data import TradeEventData


class TradeEventSender(object):
    """
    First class which ask who is listening to it
    """

    def __init__(self, event_dispatcher):
        # Save a reference to the event dispatch
        self.event_dispatcher = event_dispatcher

    def main(self):
        """
        Send a TradeEvent
        """

        self.event_dispatcher.dispatch_event(
            TradeEvent(TradeEvent.MUST_SEND_PAYMENT_STARTED_MSG_EVENT, TradeEventData('trade', 'what to do...'))
        )
