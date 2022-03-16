#!/bin/bash
source "env.sh"
$BISQ_HOME/bisq-cli --password=xyz --port=9998 settxfeerate --tx-fee-rate=50
