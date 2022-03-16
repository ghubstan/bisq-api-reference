#!/bin/bash
source "env.sh"
# Note: CLI command option parser expects a --wallet-password option, to differentiate it from an api daemon password.
$BISQ_HOME/bisq-cli --password=xyz --port=9998 removewalletpassword --wallet-password="abc"
