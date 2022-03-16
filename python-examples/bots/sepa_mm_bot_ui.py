import configparser
import sys
import threading
from decimal import *
from pathlib import Path
from tkinter import *

# Append the events pkg to the system path before importing the sepa_mm_bot.
# TODO  Is there a better way to do this?
sys.path.append('events')

import sepa_mm_bot

from events.event_dispatcher import EventDispatcher
from events.trade_event import TradeEvent
from logger import log

config = configparser.ConfigParser()
config.read('sepa_mm_bot.ini')

OUTPUT_PATH = Path(__file__).parent
ASSETS_PATH = OUTPUT_PATH / Path('./assets')
WINDOW_WIDTH = 970
WINDOW_HEIGHT = 575


def relative_to_assets(path: str) -> Path:
    return ASSETS_PATH / Path(path)


window = Tk()
window.title('Bisq API SEPA Market Maker Bot')
window_geometry = str(WINDOW_WIDTH) + 'x' + str(WINDOW_HEIGHT) + '+100+50'
window.geometry(window_geometry)
window.configure(bg='#F2F2F2')
window.iconphoto(False, PhotoImage(file=relative_to_assets('window_icon.png')))

# Initialize editable input fields from the .ini file.
price_margin_input = StringVar()
price_margin_input.set(Decimal(config.get('general', 'reference_price_margin')))

spread_input = StringVar()
spread_input.set(Decimal(config.get('general', 'spread')))

btc_amount_input = StringVar()
btc_amount_input.set(round(int(config.get('general', 'amount')) * 0.00000001, 8))

# Polling interval is not editable in UI.  Edit the .init file and restart UI.
offers_poll_interval_in_sec = int(config.get('general', 'offers_poll_interval_in_sec'))

# Payment account id is not editable in UI.
# If you want to change it, shut down the bot and edit the .ini file.
sepa_payment_account_id = config.get('general', 'sepa_payment_account_id')

# Set up event dispatcher, but delay setting up listeners until bot and canvass are created.
event_dispatcher = EventDispatcher()

# These globals are set when we handle a trade event from the event_dispatcher.
trade_id_for_start_payment_msg = None
trade_id_for_payment_received_msg = None

# Create the bot instance this UI controls.
bot = sepa_mm_bot.SepaMMBot('localhost', 9998, 'xyz', event_dispatcher)


def start():
    start_thread = threading.Thread(name='Bot', target=start_bot, args=(), daemon=False)
    start_thread.start()


def start_bot():
    disable_button(start_button)
    enable_button(stop_button)
    reference_price_margin = Decimal(price_margin_input.get())
    target_spread = Decimal(spread_input.get())
    bot.start(int(offers_poll_interval_in_sec),
              reference_price_margin,
              target_spread,
              int(Decimal(btc_amount_input.get()) * 10000000),  # Convert the BTC input amt to Satoshis.
              sepa_payment_account_id)


def stop():
    shutdown_thread = threading.Thread(name='Shutdown', target=stop_bot, args=(), daemon=False)
    shutdown_thread.start()


def stop_bot():
    global trade_id_for_start_payment_msg
    trade_id_for_start_payment_msg = None
    disable_button(payment_sent_button)
    canvas.itemconfig(manual_buy_trade_instructions, text='')

    global trade_id_for_payment_received_msg
    trade_id_for_payment_received_msg = None
    disable_button(payment_received_button)
    canvas.itemconfig(manual_sell_trade_instructions, text='')

    disable_button(stop_button)
    bot.shutdown()
    enable_button(start_button)


def send_payment_started_msg():
    log.info('payment_sent_button clicked')
    disable_button(payment_sent_button)
    global trade_id_for_start_payment_msg
    bot.send_payment_started_msg(trade_id_for_start_payment_msg)
    ui_update_text = 'Payment started message was sent for trade' + '\n' \
                     + trade_id_for_start_payment_msg
    canvas.itemconfig(manual_buy_trade_instructions, text=ui_update_text)
    trade_id_for_start_payment_msg = None


def send_payment_received_msg():
    log.info('payment_received_button clicked')
    disable_button(payment_received_button)
    global trade_id_for_payment_received_msg
    bot.send_payment_received_msg(trade_id_for_payment_received_msg)
    ui_update_text = 'Payment received confirmation message was sent for trade' + '\n' \
                     + trade_id_for_payment_received_msg
    canvas.itemconfig(manual_sell_trade_instructions, text=ui_update_text)
    trade_id_for_payment_received_msg = None


canvas = Canvas(window,
                bg='#F2F2F2',
                width=WINDOW_WIDTH - 12,
                height=WINDOW_HEIGHT - 12,
                bd=5,
                highlightthickness=0,
                relief='ridge')
canvas.place(x=0, y=0)
canvas.create_text(
    275.0,
    16.0,
    anchor='nw',
    text='SEPA EUR / BTC Market Maker Bot',
    fill='#000000',
    font=('IBMPlexSans Bold', 28 * -1))

bisq_logo_file = PhotoImage(file=relative_to_assets('bisq_logo.png'))
bisq_logo_image = canvas.create_image(116.0, 34.0, image=bisq_logo_file)

canvas.create_text(
    39.0,
    97.0,
    anchor='nw',
    text='Reference Market Price Margin:',
    fill='#000000',
    font=('IBMPlexSans SemiBold', 14 * -1))

canvas.create_text(
    395.0,
    97.0,
    anchor='nw',
    text='Spread:',
    fill='#000000',
    font=('IBMPlexSans SemiBold', 13 * -1))

canvas.create_text(
    336.0,
    97.0,
    anchor='nw',
    text='%',
    fill='#000000',
    font=('IBMPlexSans SemiBold', 14 * -1))

canvas.create_text(
    506.0,
    97.0,
    anchor='nw',
    text='%',
    fill='#000000',
    font=('IBMPlexSans SemiBold', 14 * -1))

canvas.create_text(
    568.0,
    97.0,
    anchor='nw',
    text='BTC Amount:',
    fill='#000000',
    font=('IBMPlexSans SemiBold', 14 * -1))

manual_buy_trade_instructions = canvas.create_text(
    57.0,
    226.0,
    anchor='nw',
    text='Manual Buy BTC Instructions',
    fill='#000000',
    font=('IBMPlexSans SemiBold', 14 * -1))

manual_sell_trade_instructions = canvas.create_text(
    520.0,
    226.0,
    anchor='nw',
    text='Manual Sell BTC Instructions',
    fill='#000000',
    font=('IBMPlexSans SemiBold', 14 * -1))

# https://pythonguides.com/python-tkinter-button/
# https://www.askpython.com/python-modules/tkinter/change-button-state
start_button_image = PhotoImage(file=relative_to_assets('start_button.png'))
start_button = Button(
    state=NORMAL,
    bg='#F2F2F2',
    image=start_button_image,
    borderwidth=0,
    highlightthickness=0,
    command=lambda: start(),
    relief='flat')
start_button.place(x=140.0, y=135.0, width=150.0, height=45.0)

stop_button_image = PhotoImage(file=relative_to_assets('stop_button.png'))
stop_button = Button(
    state=DISABLED,
    bg='#F2F2F2',
    image=stop_button_image,
    borderwidth=0,
    highlightthickness=0,
    command=lambda: stop(),
    relief='flat')
stop_button.place(x=600.0, y=135.0, width=150.0, height=45.0)

payment_sent_button_image = PhotoImage(file=relative_to_assets('payment_sent_button.png'))
payment_sent_button = Button(
    state=DISABLED,
    bg='#F2F2F2',
    image=payment_sent_button_image,
    borderwidth=0,
    highlightthickness=0,
    command=lambda: send_payment_started_msg(),
    relief='flat')
payment_sent_button.place(x=140.0, y=470.0, width=150.0, height=45.0)

payment_received_button__image = PhotoImage(file=relative_to_assets('payment_received_button.png'))
payment_received_button = Button(
    state=DISABLED,
    bg='#F2F2F2',
    image=payment_received_button__image,
    borderwidth=0,
    highlightthickness=0,
    command=lambda: send_payment_received_msg(),
    relief='flat')
payment_received_button.place(x=600.0, y=470.0, width=150.0, height=45.0)

reference_price_margin_image = PhotoImage(file=relative_to_assets('reference_price_margin_entry.png'))
reference_price_margin_bg = canvas.create_image(303.5, 106.5, image=reference_price_margin_image)
reference_price_margin_entry = Entry(
    bd=0,
    bg='#FFFFFF',
    highlightthickness=0,
    textvariable=price_margin_input,
    justify='right')
reference_price_margin_entry.place(x=276.0, y=96.0, width=55.0, height=19.0)

spread_image = PhotoImage(file=relative_to_assets('spread_entry.png'))
spread_bg = canvas.create_image(476.5, 104.5, image=spread_image)
spread_entry = Entry(
    bd=0,
    bg='#FFFFFF',
    highlightthickness=0,
    textvariable=spread_input,
    justify='right'
)
spread_entry.place(x=452.0, y=94.0, width=49.0, height=19.0)

btc_amount_image = PhotoImage(file=relative_to_assets('btc_amount_entry.png'))
btc_amount_bg = canvas.create_image(749.0, 104.5, image=btc_amount_image)
btc_amount_entry = Entry(
    bd=0,
    bg='#FFFFFF',
    highlightthickness=0,
    textvariable=btc_amount_input,
    justify='right')
btc_amount_entry.place(x=671.0, y=94.0, width=156.0, height=19.0)


def disable_button(button):
    button['state'] = DISABLED


def enable_button(button):
    button['state'] = NORMAL


# Event handling method must be declared before listeners are set up.
def handle_trade_event(event):
    log.info('\tHandling %s...', str(event.type))
    trade = event.data.trade
    instructions = event.data.instructions
    trade_summary = bot.get_trade_payment_summary(trade)
    ui_update_text = instructions + '\n\n' + trade_summary
    if event.type == TradeEvent.MUST_SEND_PAYMENT_STARTED_MSG_EVENT:
        global trade_id_for_start_payment_msg
        if trade_id_for_start_payment_msg is None:
            canvas.itemconfig(manual_buy_trade_instructions, text=ui_update_text)
            trade_id_for_start_payment_msg = trade.trade_id
            enable_button(payment_sent_button)
        else:
            log.warning('Already waiting for manual step for trade %s', trade_id_for_start_payment_msg)
    elif event.type == TradeEvent.MUST_SEND_PAYMENT_RECEIVED_MSG_EVENT:
        global trade_id_for_payment_received_msg
        if trade_id_for_payment_received_msg is None:
            canvas.itemconfig(manual_sell_trade_instructions, text=ui_update_text)
            trade_id_for_payment_received_msg = trade.trade_id
            enable_button(payment_received_button)
        else:
            log.warning('Already waiting for manual step for trade %s', trade_id_for_payment_received_msg)
    else:
        raise 'Invalid event type ' + event.type


event_dispatcher.add_event_listener(TradeEvent.MUST_SEND_PAYMENT_STARTED_MSG_EVENT, handle_trade_event)
event_dispatcher.add_event_listener(TradeEvent.MUST_SEND_PAYMENT_RECEIVED_MSG_EVENT, handle_trade_event)

window.resizable(False, False)
window.mainloop()
