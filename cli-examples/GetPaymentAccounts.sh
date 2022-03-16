#!/bin/bash
source "env.sh"
# Get list of all saved payment accounts, including altcoin accounts.
$BISQ_HOME/bisq-cli --password=xyz --port=9998 getpaymentaccts
