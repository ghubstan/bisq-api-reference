# From https://expobrain.net/2010/07/31/simple-event-dispatcher-in-python
# Create and instance of event dispatcher
from event_dispatcher import EventDispatcher
from trade_event_listener import TradeEventListener
from trade_event_sender import TradeEventSender

dispatcher = EventDispatcher()

sender = TradeEventSender(dispatcher)
listener = TradeEventListener(dispatcher)
sender.main()
