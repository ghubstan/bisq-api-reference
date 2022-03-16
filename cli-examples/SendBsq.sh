#!/bin/bash
source "env.sh"
$BISQ_HOME/bisq-cli --password=xyz --port=9998 sendbsq \
  --address=Bbcrt1q9elrmtxtzpwt25zq2pmeeu6qk8029w404ad0xn \
  --amount=1000.10
