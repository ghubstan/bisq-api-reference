#!/bin/bash
source "env.sh"
$BISQ_HOME/bisq-cli --password=xyz --port=9998 getbalance
$BISQ_HOME/bisq-cli --password=xyz --port=9998 getbalance --currency-code=BSQ
$BISQ_HOME/bisq-cli --password=xyz --port=9998 getbalance --currency-code=BTC
