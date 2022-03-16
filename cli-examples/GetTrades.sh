#!/bin/bash
source "env.sh"
# Get currently open trades.
$BISQ_HOME/bisq-cli --password=xyz --port=9998 gettrades
$BISQ_HOME/bisq-cli --password=xyz --port=9998 gettrades --category=open

# Get completed trades.
$BISQ_HOME/bisq-cli --password=xyz --port=9998 gettrades --category=closed

# Get failed trades.
$BISQ_HOME/bisq-cli --password=xyz --port=9998 gettrades --category=failed
