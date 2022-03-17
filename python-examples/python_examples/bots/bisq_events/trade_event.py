# From https://expobrain.net/2010/07/31/simple-event-dispatcher-in-python
from python_examples.bots.bisq_events.event import Event


class TradeEvent(Event):
    """
    When subclassing Event class the only thing you must do is to define
    a list of class level constants which defines the event types and the
    string associated to them
    """

    MUST_SEND_PAYMENT_STARTED_MSG_EVENT = 'MUST_SEND_PAYMENT_STARTED_MSG_EVENT'
    MUST_SEND_PAYMENT_RECEIVED_MSG_EVENT = 'MUST_SEND_PAYMENT_RECEIVED_MSG_EVENT'
