#!/bin/bash
source "env.sh"
# Get most recently available market price of XMR in BTC.
$BISQ_HOME/bisq-cli --password=xyz --port=9998 getbtcprice --currency-code=XMR
