#!/bin/bash
source "env.sh"
# Get my BSQ swap offers to buy BTC for BSQ.
$BISQ_HOME/bisq-cli --password=xyz --port=9998 getmyoffers --direction=BUY --currency-code=BSQ
# Get my BSQ swap offers to sell BTC for BSQ.
$BISQ_HOME/bisq-cli --password=xyz --port=9998 getmyoffers --direction=SELL --currency-code=BSQ
