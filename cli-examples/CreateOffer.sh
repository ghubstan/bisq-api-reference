#!/bin/bash
source "env.sh"

# Create a fixed-price offer to buy BTC with EUR.
$BISQ_HOME/bisq-cli --password=xyz --port=9998 createoffer \
  --payment-account-id=f3c1ec8b-9761-458d-b13d-9039c6892413 \
  --direction=BUY \
  --currency-code=EUR \
  --amount=0.125 \
  --min-amount=0.0625 \
  --fixed-price=34500 \
  --security-deposit=15.0 \
  --fee-currency=BSQ

# Create a market-price-margin based offer to sell BTC for JPY, at 0.5% above the current market JPY price.
$BISQ_HOME/bisq-cli --password=xyz --port=9998 createoffer \
  --payment-account-id=f3c1ec8b-9761-458d-b13d-9039c6892413 \
  --direction=SELL \
  --currency-code=JPY \
  --amount=0.125 \
  --min-amount=0.0625 \
  --market-price-margin=0.5 \
  --security-deposit=15.0 \
  --fee-currency=BSQ

# Create an offer to swap 0.5 BTC for BSQ, at a price of 0.00005 BTC for 1 BSQ
$BISQ_HOME/bisq-cli --password=xyz --port=9998 createoffer \
  --swap=true \
  --direction=BUY \
  --currency-code=BSQ \
  --amount=0.5 \
  --fixed-price=0.00005
