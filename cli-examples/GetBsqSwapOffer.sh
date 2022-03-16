#!/bin/bash
source "env.sh"
$BISQ_HOME/bisq-cli --password=xyz --port=9998 getoffer \
  --offer-id=83e8b2e2-51b6-4f39-a748-3ebd29c22aea
