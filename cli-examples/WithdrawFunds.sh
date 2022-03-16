#!/bin/bash
source "env.sh"
# Withdraw BTC trade proceeds to external bitcoin wallet.
$BISQ_HOME/bisq-cli --password=xyz --port=9998 withdrawfunds \
  --trade-id=83e8b2e2-51b6-4f39-a748-3ebd29c22aea \
  --address=bcrt1qqau7ad7lf8xx08mnxl709h6cdv4ma9u3ace5k2 \
  --memo="Optional memo saved with transaction in Bisq wallet."
