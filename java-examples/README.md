# Bisq API Java Examples And Bots

This subproject contains:

* [Java API rpc examples](https://github.com/bisq-network/bisq-api-reference/tree/main/java-examples/src/main/java/bisq/rpccalls)
  demonstrating how to send API gRPC requests, and handle responses.

* [Java API bots](https://github.com/bisq-network/bisq-api-reference/tree/main/java-examples/src/main/java/bisq/bots)
  to use as is on mainnet, and demonstrate how to write new API bots. If interested in porting any bot code to other
  languages supported by [gRPC](https://grpc.io/docs/languages), please use these Java bot examples, not the Python
  examples. The Python examples were written by a Python noob, and don't handle errors.

* A [Gradle build file](https://github.com/bisq-network/bisq-api-reference/blob/main/java-examples/build.gradle)
  that could be used as a template for your own Java API bot project.

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

### Run Bots On Mainnet At Your Own Risk

This document would not state "these bots can be run on mainnet, as is" without reasonable confidence on the part of the
code's author, but if you do, you do so at your own risk. Copious details about running them on a local BTC regtest
network, running on mainnet in **dryrun** mode, and each bot's configuration is provided in this document. Please put
some effort into understanding a bot's code and its configuration before trying it on mainnet.

### Why There Is Duplicated Code In The Bots

The TakeBestPricedOffer* bots could be combined into a single class, and use multithreaded task scheduling instead of
loops with Thread sleep instructions, but we want them to be easily understood by people who are not necessarily
experienced Java coders (and be easily portable to other [gRPC supported language bindings](https://grpc.io/docs/languages)).
For non-developers, splitting up a one-size-fits-all TakeBestPricedOffer also makes them easier to configure for various
BTC/Fiat, XMR/BTC, and BSQ/BTC market use cases.

## Generating Protobuf Code

### Download IDL (.proto) Files From The [Bisq Repo](https://github.com/bisq-network/bisq)

The protobuf IDL files are not part of this project, and must be downloaded from the Bisq repository's 
[protobuf file directory](https://github.com/bisq-network/bisq/tree/master/proto/src/main/proto).

TODO @ripcurlx, please review https://github.com/ghubstan/bisq-api-reference/pull/11

You can download them by running
[this script](https://github.com/bisq-network/bisq-api-reference/blob/main/proto-downloader/download-bisq-protos.sh)
from your IDE or a shell:

```asciidoc
$ proto-downloader/download-bisq-protos.sh
```

The java-examples [build file](https://github.com/bisq-network/bisq-api-reference/blob/main/java-examples/build.gradle)
will generate the code from the downloaded IDL (.proto) files.

You should be able to generate the protobuf Java sources and all Java examples in your IDE.

In a terminal:

```asciidoc
$ cd java-examples $ ./gradlew clean build
```

## Java API Method Examples

Each class in
the [bisq.rpccalls](https://github.com/bisq-network/bisq-api-reference/tree/main/java-examples/src/main/java/bisq/rpccalls)
package is named for the RPC method call being demonstrated.

Their purpose is to show how to construct a gRPC service method request with parameters, send the request, and print a
response object if there is one. As a rule, the request is successful if a gRPC StatusRuntimeException is not thrown
by the API daemon.

Their usage is simple; there are no program arguments for any of them. Just run them with an IDE program launcher or
your shell.  However, you will usually need to edit the Java class and re-compile it before running it because these 
examples know nothing about real Payment Account IDs, Offer IDs, etc. To run the
[GetOffer.java](https://github.com/bisq-network/bisq-api-reference/blob/main/java-examples/src/main/java/bisq/rpccalls/GetOffer.java)
example, your will need to change the hard-coded offer ID to a real offer ID to avoid a "not found" gRPC 
StatusRuntimeException from the API daemon.

## Java API Bots

### Purpose

The Java API bots in this project are meant to be run on mainnet, provide a base for more complex bots, and guide you
in developing your own bots.

### Run on mainnet at your own risk!

You need to understand a bot's code and its configuration before trying it on mainnet. While you get familiar with a bot
example, you can run it in **dryrun** mode to see how it behaves with different configurations (more later). Even
better:  run it while your Bisq API daemons (seednode, arbitration-node, and a trading peer) are connected to a local
BTC regtest network.
The [API test harness](https://github.com/bisq-network/bisq/blob/master/apitest/docs/api-beta-test-guide.md) is
convenient for this.

#### Quick And Dirty Test Harness

If you are already familiar
with [building Bisq source code](https://github.com/bisq-network/bisq/blob/master/docs/build.md), and
have [bitcoin-core binaries](https://github.com/bitcoin/bitcoin) on your system's $PATH, you might skip the
[API test harness setup guide](https://github.com/bisq-network/bisq/blob/master/apitest/docs/api-beta-test-guide.md).

Before you try the test harness, make sure your host is not running any bitcoind or Bisq nodes.

Clone the Bisq master branch to your host, build and start it:

```asciidoc
#Clone Bisq source repo.
$ git clone https://github.com/bisq-network/bisq.git [some folder]
$ cd [some folder]

#Build Bisq source, install DAO/Regtest wallet files.
$ ./gradlew clean build :apitest:installDaoSetup -x test

#Start local bitcoind (regtest) node and headless test harness nodes.
$ ./bisq-apitest --apiPassword=xyz --supportingApps=bitcoind,seednode,arbdaemon,alicedaemon,bobdaemon --shutdownAfterTests=false
```

To shut down the test harness, enter **^C**.

#### Creating And Using Runnable Bot Jars

TODO (more later)

### Take BSQ / BTC / XMR / Offer Bots

There are four bots for taking offers:

* [TakeBestPricedOfferToBuyBsq (From You)](https://github.com/bisq-network/bisq-api-reference/blob/split-up-take-btc-offer-bots/java-examples/src/main/java/bisq/bots/TakeBestPricedOfferToBuyBsq.java)
* [TakeBestPricedOfferToSellBsq (To You)](https://github.com/bisq-network/bisq-api-reference/blob/split-up-take-btc-offer-bots/java-examples/src/main/java/bisq/bots/TakeBestPricedOfferToSellBsq.java)

* [TakeBestPricedOfferToBuyBtc (From You)](https://github.com/bisq-network/bisq-api-reference/blob/split-up-take-btc-offer-bots/java-examples/src/main/java/bisq/bots/TakeBestPricedOfferToBuyBtc.java)
* [TakeBestPricedOfferToSellBtc (To You)](https://github.com/bisq-network/bisq-api-reference/blob/split-up-take-btc-offer-bots/java-examples/src/main/java/bisq/bots/TakeBestPricedOfferToSellBtc.java)

* [TakeBestPricedOfferToBuyXmr (From You)](https://github.com/bisq-network/bisq-api-reference/blob/split-up-take-btc-offer-bots/java-examples/src/main/java/bisq/bots/TakeBestPricedOfferToBuyXmr.java)
* [TakeBestPricedOfferToSellXmr (To You)](https://github.com/bisq-network/bisq-api-reference/blob/split-up-take-btc-offer-bots/java-examples/src/main/java/bisq/bots/TakeBestPricedOfferToSellXmr.java)

The **Take Buy/Sell BTC** bots take 1 or more offers for a given criteria as defined in each bot's configuration file
(more later). After the configured maximum number of offers have been taken, the bot shuts down the API daemon and
itself because trade payments are made outside Bisq. The Bisq API *cannot automate the fiat or XMR trade payment and
receipt confirmation steps of the protocol*. When an offer is taken by the API, the trade payment steps of the protocol
must be performed in the UI.

The **Take Buy/Sell BSQ** bots take 1 or more offers for a given criteria as defined in each bot's configuration file
(more later). After the configured maximum number of offers have been taken, the bot shuts down itself, but not the API
daemon. BSQ Swaps can be fully automated by the API because the swap transaction is completed within seconds of taking a
BSQ Swap offer.

#### [TakeBestPricedOfferToSellBtc (To You)](https://github.com/bisq-network/bisq-api-reference/blob/split-up-take-btc-offer-bots/java-examples/src/main/java/bisq/bots/TakeBestPricedOfferToSellBtc.java)

**Purpose**

This bot waits for attractively priced SELL BTC offers to appear, takes the offers, then shuts down both the API daemon
and itself (the bot). The user should then proceed to start the desktop UI application and complete the new trades.

The benefit this bot provides is freeing up the user time spent watching the offer book in the UI, waiting for the right
offer to take. Low-priced BTC offers are taken relatively quickly; this bot increases the chance of beating the other
nodes at taking the offer.

The disadvantage is that if the user takes offers with the API, she must complete the trades with the desktop UI. This
problem is due to the inability of the API to fully automate every step of the trading protocol. Sending fiat or XMR
payments, and confirming their receipt, are manual activities performed outside the Bisq daemon and desktop UI. Also,
the API and the desktop UI cannot run at the same time. Care must be taken to shut down one before starting the other.

The criteria for determining which offers to take are defined in the bot's configuration file
TakeBestPricedOfferToSellBtc.properties (located in project's src/main/resources directory). The individual
configurations are commented in the existing TakeBestPricedOfferToSellBtc.properties, which should be used as a template
for your own use case.

**Use Cases**

One possible use case for this bot is to buy BTC with GBP, e.g., take a "Faster Payment" offer to sell BTC (to you) for
GBP at or below the current market GBP price if:

* the offer maker is a preferred trading peer
* the offer's BTC amount is between 0.10 and 0.25 BTC
* the current transaction mining fee rate is less than or equal 20 sats / byte

**Class Doc Link**

**Configuration**

* Param 1
* Param 2
* Param 3
* ...

**Usage**

```asciidoc
$ java -jar x.jar --password=xyz --conf=[path.conf] --dryrun=true
```

## Gradle Build File

This
project's [Gradle build file](https://github.com/bisq-network/bisq-api-reference/blob/main/java-examples/build.gradle),
shows how to generate the necessary protobuf classes from the Bisq .proto files.


