#!/bin/bash
source "env.sh"

# Take a BSQ swap offer.
$BISQ_HOME/bisq-cli --password=xyz --port=9998 takeoffer --offer-id=8368b2e2-anb6-4ty9-ab09-3ebdk34f2aea

# Take an offer that is not a BSQ swap offer.
# The payment-account-id param is required, the fee-currency param is optional.
$BISQ_HOME/bisq-cli --password=xyz --port=9998 takeoffer \
    --offer-id=83e8b2e2-51b6-4f39-a748-3ebd29c22aea \
    --payment-account-id=fe20cdbd-22be-4b8a-a4b6-d2608ff09d6e \
    --fee-currency=BTC
