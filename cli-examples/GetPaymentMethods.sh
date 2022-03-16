#!/bin/bash
source "env.sh"
# Get the ids of all supported Bisq payment methods.
$BISQ_HOME/bisq-cli --password=xyz --port=9998 getpaymentmethods
