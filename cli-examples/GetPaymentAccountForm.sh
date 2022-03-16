#!/bin/bash
source "env.sh"
# Get a blank SWIFT payment account form and save the json file in the current working directory.
$BISQ_HOME/bisq-cli --password=xyz --port=9998 getpaymentacctform --payment-method-id=SWIFT
