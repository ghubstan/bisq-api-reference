#!/bin/bash
source "env.sh"
# Send message to BTC buyer that payment has been received.
$BISQ_HOME/bisq-cli --password=xyz --port=9998 confirmpaymentreceived \
  --trade-id=83e8b2e2-51b6-4f39-a748-3ebd29c22aea
