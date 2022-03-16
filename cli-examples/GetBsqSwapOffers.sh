#!/bin/bash
source "env.sh"
# Get available BSQ swap offers to buy BTC with BSQ.
$BISQ_HOME/bisq-cli --password=xyz --port=9998 getoffers --direction=BUY --currency-code=BSQ
# Get available BSQ swap offers to sell BTC for BSQ.
$BISQ_HOME/bisq-cli --password=xyz --port=9998 getoffers --direction=SELL --currency-code=BSQ
