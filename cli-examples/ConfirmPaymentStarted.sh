#!/bin/bash
source "env.sh"
# Send message to BTC seller that payment has been sent.
$BISQ_HOME/bisq-cli --password=xyz --port=9998 confirmpaymentstarted \
  --trade-id=83e8b2e2-51b6-4f39-a748-3ebd29c22aea
