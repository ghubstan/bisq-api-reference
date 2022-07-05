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

## [Risks, Warnings and Flaws](#risks-warnings-and-flaws)

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
experienced Java coders (and be easily portable to
other [gRPC supported language bindings](https://grpc.io/docs/languages)). For non-developers, splitting up a
one-size-fits-all TakeBestPricedOffer also makes them easier to configure for various BTC/Fiat, XMR/BTC, and BSQ/BTC
market use cases.

## [Generating Protobuf Code](#generating-protobuf-code)

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

## [Java API Method Examples](#java-api-method-examples)

Each class in
the [bisq.rpccalls](https://github.com/bisq-network/bisq-api-reference/tree/main/java-examples/src/main/java/bisq/rpccalls)
package is named for the RPC method call being demonstrated.

Their purpose is to show how to construct a gRPC service method request with parameters, send the request, and print a
response object if there is one. As a rule, the request is successful if a gRPC StatusRuntimeException is not thrown by
the API daemon.

Their usage is simple; there are no program arguments for any of them. Just run them with an IDE program launcher or
your shell. However, you will usually need to edit the Java class and re-compile it before running it because these
examples know nothing about real Payment Account IDs, Offer IDs, etc. To run the
[GetOffer.java](https://github.com/bisq-network/bisq-api-reference/blob/main/java-examples/src/main/java/bisq/rpccalls/GetOffer.java)
example, your will need to change the hard-coded offer ID to a real offer ID to avoid a "not found" gRPC
StatusRuntimeException from the API daemon.

## [Java API Bots](#java-api-bots)

### Purpose

The Java API bots in this project are meant to be run on mainnet, provide a base for more complex bots, and guide you in
developing your own bots.

Put some effort into understanding a bot's code and its configuration before trying it on mainnet. While you get
familiar with a bot example, you can run it in **dryrun** mode to see how it behaves with different configurations
(more later). Even better:  run it while your Bisq API daemons (seednode, arbitration-node, and a trading peer) are
connected to a local BTC regtest network.
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
# Clone Bisq source repo.
$ git clone https://github.com/bisq-network/bisq.git [some folder]
$ cd [some folder]

# Build Bisq source, install DAO/Regtest wallet files (with coins).
$ ./gradlew clean build :apitest:installDaoSetup -x test

# Start local bitcoind (regtest) node and headless test harness nodes.
$ ./bisq-apitest --apiPassword=xyz --supportingApps=bitcoind,seednode,arbdaemon,alicedaemon,bobdaemon --shutdownAfterTests=false
```

To shut down the test harness, enter **^C**.

### Take BSQ / BTC / XMR / Offer Bots

There are six bots for taking offers:

* [Take Best Priced Offer To Buy Btc](#take-best-priced-offer-to-buy-btc)
* [Take Best Priced Offer To Sell Btc](#take-best-priced-offer-to-sell-btc)

* [Take Best Priced Offer To Buy Bsq](#take-best-priced-offer-to-buy-bsq)
* [Take Best Priced Offer To Sell Bsq](#take-best-priced-offer-to-sell-bsq)

* [Take Best Priced Offer To Buy Xmr](#take-best-priced-offer-to-buy-xmr)
* [Take Best Priced Offer To Sell Xmr](#take-best-priced-offer-to-sell-xmr)

The **Take Buy/Sell BTC and XMR Offer** bots take 1 or more offers for a given criteria as defined in each bot's
configuration file (more later). After the configured maximum number of offers have been taken, the bot shuts down the
API daemon and itself because trade payments are made outside Bisq. Bisq nodes (UI or API) do not communicate with your
banks and XMR wallets, and *cannot automate fiat and XMR trade payments and deposit confirmations*.

The Bisq trade payment protocol steps of the Bisq protocol can be performed in the UI, or you can finish the trade with
an API daemon and manual CLI commands:

```asciidoc
# If you are a BTC buyer, notify peer you have initiated fiat or XMR payment.
$ ./bisq-cli --password=xyz --port=9998 confirmpaymentstarted --trade-id=<trade-id>

# If you are a BTC seller, notify peer your have received fiat or XMR payment.
$ ./bisq-cli --password=xyz --port=9998 confirmpaymentreceived --trade-id=<trade-d>

# Close your completed trade (move it to your trade history).  
$ ./bisq-cli --password=xyz --port=9998 closetrade --trade-id=<trade-id>
```

The **Take Buy/Sell BSQ** bots take 1 or more offers for a given criteria as defined in each bot's configuration file
(more later). After the configured maximum number of offers have been taken, the bot shuts down itself, but not the API
daemon. BSQ Swaps can be fully automated by the API because the swap transaction is completed within seconds of taking a
BSQ Swap offer.

### [Take Best Priced Offer To Buy Btc](#take-best-priced-offer-to-buy-btc)

**Purpose:**  Sell your BTC at a higher fiat price

This bot waits for attractively priced BUY BTC (with fiat) offers to appear, and takes 1 or more of them according to
the bot's configuration. The bot will consider only those offers created with the same payment method associated with
your bot's configured payment account id.

The benefit this bot provides is freeing up user time spent watching the offer book in the UI, waiting for the right
offer to take. The disadvantage is having to perform some trade protocol steps outside the API daemon. 
Fiat payment confirmations must be done as they are when using the Bisq UI -- outside the Bisq application. After bank 
payments are confirmed in your online banking system, trades can be completed in the desktop UI, or via API CLI commands.

**Warning**

Take special care to not run the Bisq API daemon and the desktop application at the same time on the same host.

**Use Cases, Usage and Configuration**

This information is found in
the [source code's Java class level documentation](https://github.com/bisq-network/bisq-api-reference/blob/main/java-examples/src/main/java/bisq/bots/TakeBestPricedOfferToBuyBtc.java)
.

**Creating And Using Runnable TakeBestPricedOfferToBuyBtc Jar**

To create a runnable jar, see [Creating Runnable Jars](#creating-runnable-jars). To run the jar, edit the conf file for
your use case, and use the java -jar command:

```asciidoc
$ java -jar take-best-priced-offer-to-buy-btc.jar \
    --password=xyz \
    --dryrun=false \
    --conf=take-best-priced-offer-to-buy-btc.conf 
```

### [Take Best Priced Offer To Sell Btc](#take-best-priced-offer-to-sell-btc)

**Purpose:**  Buy BTC at a lower fiat price

This bot waits for attractively priced SELL BTC (for fiat) offers to appear, and takes 1 or more of them according to
the bot's configuration. The bot will consider only those offers created with the same payment method associated with
your bot's configured payment account id.

The benefit this bot provides is freeing up user time spent watching the offer book in the UI, waiting for the right
offer to take. Low-priced BTC offers are taken relatively quickly; this bot increases the chance of beating other nodes
to the offer. The disadvantage is having to perform some trade protocol steps outside the API daemon. Fiat 
payments must be sent as they are when using the Bisq UI -- outside the Bisq application. After bank payments are made
in your online banking system, trades can be completed in the desktop UI, or via API CLI commands.

**Warning**

Take special care to not run the Bisq API daemon and the desktop application at the same time on the same host.

**Use Cases, Usage and Configuration**

This information is found in
the [source code's Java class level documentation](https://github.com/bisq-network/bisq-api-reference/blob/main/java-examples/src/main/java/bisq/bots/TakeBestPricedOfferToSellBtc.java)
.

**Creating And Using Runnable TakeBestPricedOfferToSellBtc Jar**

To create a runnable jar, see [Creating Runnable Jars](#creating-runnable-jars). To run the jar, edit the conf file for
your use case, and use the java -jar command:

```asciidoc
$ java -jar take-best-priced-offer-to-sell-btc.jar \
    --password=xyz \
    --dryrun=false \
    --conf=take-best-priced-offer-to-sell-btc.conf 
```

### [Take Best Priced Offer To Buy Bsq](#take-best-priced-offer-to-buy-bsq)

**Purpose:**  Sell your BSQ at a higher BTC price

This bot waits for attractively priced BUY BSQ (with BTC) offers to appear, and takes 1 or more of them according to
the bot's configuration. The bot will consider only those offers created with the same payment method associated with
your bot's configured payment account id.

**Warning**

Take special care to not run the Bisq API daemon and the desktop application at the same time on the same host.

**Use Cases, Usage and Configuration**

This information is found in
the [source code's Java class level documentation](https://github.com/bisq-network/bisq-api-reference/blob/main/java-examples/src/main/java/bisq/bots/TakeBestPricedOfferToBuyBsq.java)
.

**Creating And Using Runnable TakeBestPricedOfferToBuyBsq Jar**

To create a runnable jar, see [Creating Runnable Jars](#creating-runnable-jars). To run the jar, edit the conf file for
your use case, and use the java -jar command:

```asciidoc
$ java -jar take-best-priced-offer-to-buy-bsq.jar \
    --password=xyz \
    --dryrun=false \
    --conf=take-best-priced-offer-to-buy-bsq.conf 
```

### [Take Best Priced Offer To Sell Bsq](#take-best-priced-offer-to-sell-bsq)

**Purpose:**  Buy BSQ at a lower BTC price

This bot waits for attractively priced SELL BSQ (for BTC) offers to appear, and takes 1 or more of them according to
the bot's configuration. The bot will consider only those offers created with the same payment method associated with
your bot's configured payment account id.

**Warning**

Take special care to not run the Bisq API daemon and the desktop application at the same time on the same host.

**Use Cases, Usage and Configuration**

This information is found in
the [source code's Java class level documentation](https://github.com/bisq-network/bisq-api-reference/blob/main/java-examples/src/main/java/bisq/bots/TakeBestPricedOfferToSellBsq.java)
.

**Creating And Using Runnable TakeBestPricedOfferToSellBsq Jar**

To create a runnable jar, see [Creating Runnable Jars](#creating-runnable-jars). To run the jar, edit the conf file for
your use case, and use the java -jar command:

```asciidoc
$ java -jar take-best-priced-offer-to-sell-bsq.jar \
    --password=xyz \
    --dryrun=false \
    --conf=take-best-priced-offer-to-sell-bsq.conf 
```

### [Take Best Priced Offer To Buy Xmr](#take-best-priced-offer-to-buy-xmr)

**Purpose:**  Sell your XMR at a higher BTC price

This bot waits for attractively priced BUY XMR (with BTC) offers to appear, and takes 1 or more of them according to
the bot's configuration. The bot will consider only those offers created with the same payment method associated with
your bot's configured payment account id.

**Warning**

Take special care to not run the Bisq API daemon and the desktop application at the same time on the same host.

**Use Cases, Usage and Configuration**

This information is found in
the [source code's Java class level documentation](https://github.com/bisq-network/bisq-api-reference/blob/main/java-examples/src/main/java/bisq/bots/TakeBestPricedOfferToBuyXmr.java)
.

**Creating And Using Runnable TakeBestPricedOfferToBuyXmr Jar**

To create a runnable jar, see [Creating Runnable Jars](#creating-runnable-jars). To run the jar, edit the conf file for
your use case, and use the java -jar command:

```asciidoc
$ java -jar take-best-priced-offer-to-buy-xmr.jar \
    --password=xyz \
    --dryrun=false \
    --conf=take-best-priced-offer-to-buy-xmr.conf 
```

### [Take Best Priced Offer To Sell Xmr](#take-best-priced-offer-to-sell-xmr)

**Purpose:**  Buy XMR at a lower BTC price

This bot waits for attractively priced SELL XMR (for BTC) offers to appear, and takes 1 or more of them according to
the bot's configuration. The bot will consider only those offers created with the same payment method associated with
your bot's configured payment account id.

**Warning**

Take special care to not run the Bisq API daemon and the desktop application at the same time on the same host.

**Use Cases, Usage and Configuration**

This information is found in
the [source code's Java class level documentation](https://github.com/bisq-network/bisq-api-reference/blob/main/java-examples/src/main/java/bisq/bots/TakeBestPricedOfferToSellXmr.java)
.

**Creating And Using Runnable TakeBestPricedOfferToSellXmr Jar**

To create a runnable jar, see [Creating Runnable Jars](#creating-runnable-jars). To run the jar, edit the conf file for
your use case, and use the java -jar command:

```asciidoc
$ java -jar take-best-priced-offer-to-sell-xmr.jar \
    --password=xyz \
    --dryrun=false \
    --conf=take-best-priced-offer-to-sell-xmr.conf 
```

### [Creating Runnable Jars](#creating-runnable-jars)

You can create runnable jars for these bots and run them in a terminal. After building the java-examples project, run
the script **java-examples/scripts/create-bot-jars.sh**. You can run the jar from the
**java-examples/scripts/java-examples** folder created by the script, or copy that folder where you like and run it from
there.

Here are the steps to create runnable bot jars.

```asciidoc
# Build the java-examples project.
$ cd java-examples
$ ./gradlew clean build

# Build the runnable bot jars.  Each jar contains one class, with its dependencies defined in its MANIFEST.MF.
$ scripts/create-bot-jars.sh
```

Each jar has its own conf file, generated from the bot source code's properties file. For example,

* take-best-priced-offer-to-buy-btc.jar
* take-best-priced-offer-to-buy-btc.conf

are created from

* TakeBestPricedOfferToBuyBtc.java
* TakeBestPricedOfferToBuyBtc.properties

To run it, edit the conf file for your use case and run a java -jar command:

```asciidoc
$ java -jar take-best-priced-offer-to-sell-btc.jar \
    --password=xyz \
    --dryrun=false \
    --conf=take-best-priced-offer-to-sell-btc.conf 
```

You can rename a conf file as you like, and save several copies for specific use cases.

## [Gradle Build File](#gradle-build-file)

This
project's [Gradle build file](https://github.com/bisq-network/bisq-api-reference/blob/main/java-examples/build.gradle),
shows how to generate the necessary protobuf classes from the Bisq .proto files.


