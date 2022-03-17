# From https://expobrain.net/2010/07/31/simple-event-dispatcher-in-python
class Event(object):
    """
    Generic event to use with EventDispatcher.
    """

    def __init__(self, event_type, data=None):
        """
        The constructor accepts an event type as string and a custom data
        """
        self._type = event_type
        self._data = data

    @property
    def type(self):
        """
        Returns the event type
        """
        return self._type

    @property
    def data(self):
        """
        Returns the data associated to the event
        """
        return self._data
