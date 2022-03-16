#!/bin/bash
source "env.sh"
# Get my offers to buy BTC with EUR.
$BISQ_HOME/bisq-cli --password=xyz --port=9998 getmyoffers --direction=BUY --currency-code=EUR
# Get my offers to sell BTC for EUR.
$BISQ_HOME/bisq-cli --password=xyz --port=9998 getmyoffers --direction=SELL --currency-code=EUR
