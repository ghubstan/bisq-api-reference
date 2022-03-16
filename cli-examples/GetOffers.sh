#!/bin/bash
source "env.sh"
# Get available offers to buy BTC with JPY.
$BISQ_HOME/bisq-cli --password=xyz --port=9998 getoffers --direction=BUY --currency-code=JPY
# Get available offers to sell BTC for JPY.
$BISQ_HOME/bisq-cli --password=xyz --port=9998 getoffers --direction=SELL --currency-code=JPY
