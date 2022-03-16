#!/bin/bash
source "env.sh"
$BISQ_HOME/bisq-cli --password=xyz --port=9998 gettransaction \
  --transaction-id=fef206f2ada53e70fd8430d130e43bc3994ce075d50ac1f4fda8182c40ef6bdd
