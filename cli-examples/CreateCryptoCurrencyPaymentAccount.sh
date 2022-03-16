#!/bin/bash
source "env.sh"
$BISQ_HOME/bisq-cli --password=xyz --port=9998 createcryptopaymentacct \
  --account-name="My XMR Payment Account" \
  --currency-code=XMR \
  --address=4AsjtNXChh3Va58czCWHjn9S8ZFnsxggGZoSePauBHmSMr8vY5aBSqrPtQ9Y9M1iwkBHxcuTWXJsJ4NDATQjQJyKBXR7WP7 \
  --trade-instant=false
