# Bisq API Python Examples

This subproject contains Python3 classes demonstrating API gRPC method calls, and some sample bots.

Each class in
the [bisq.rpccalls](https://github.com/bisq-network/bisq-api-reference/tree/main/python-examples/bisq/rpccalls) package
is named for the RPC method call being demonstrated.

The [bisq.bots](https://github.com/bisq-network/bisq-api-reference/tree/main/python-examples/bisq/rpccalls) package
contains some simple bots. Please do not run the Python bot examples on mainnet.
See [warning](#bot-not-ready-for-mainnet).

The `run-setup.sh` script in this directory can install Python3 dependencies and example packages into a local venv.

## Risks, Warnings and Flaws

### Never Run API Daemon and [Bisq GUI](https://bisq.network) On Same Host At Same Time

The API daemon and the GUI share the same default wallet and connection ports. Beyond inevitable failures due to
fighting over the wallet and ports, doing so will probably corrupt your wallet. Before starting the API daemon, make
sure your GUI is shut down, and vice-versa. Please back up your mainnet wallet early and often with the GUI.

### Go Slow (But Much Faster Than You Click Buttons In The GUI)

[Bisq](https://bisq.network) was designed to respond to manual clicks in the user interface. It is not a
high-throughput, high-performance system supporting atomic transactions. Care must be taken to avoid problems due to
slow wallet updates on your disk, and Tor network latency. The API daemon enforces limits on request frequency via call
rate metering, but you cannot assume bots can perform tasks as rapidly as the API daemon's call rate meters allow.

### [Do Not Run Python Bot Examples On Mainnet](#bot-not-ready-for-mainnet)

The scripts in the [bisq.bots](https://github.com/bisq-network/bisq-api-reference/tree/main/python-examples/bisq/bots)
package should not be run on mainnet. They do not properly handle errors, and were written by a Python noob.

The [Java Bot Examples](https://github.com/bisq-network/bisq-api-reference/blob/split-up-take-btc-offer-bots/java-examples/README.md)
are intended to be run on mainnet. An experienced Python developer could port these examples to Python for running on
mainnet, and offer them as a contribution to
the [Bisq API Reference](https://github.com/bisq-network/bisq-api-reference)
project. If accepted, they could be [compensated](https://bisq.wiki/Making_a_compensation_request).