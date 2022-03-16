#!/bin/bash
source "env.sh"
$BISQ_HOME/bisq-cli --password=xyz --port=9998 createoffer \
  --swap=true \
  --direction=BUY \
  --currency-code=BSQ \
  --amount=0.50 \
  --min-amount=0.25 \
  --fixed-price=0.00005
