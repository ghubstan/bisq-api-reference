#!/bin/bash
source "env.sh"
$BISQ_HOME/bisq-cli --password=xyz --port=9998 sendbtc \
  --address=bcrt1qqau7ad7lf8xx08mnxl709h6cdv4ma9u3ace5k2 \
  --amount=0.006 \
  --tx-fee-rate=20
