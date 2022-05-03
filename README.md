# Bisq API Reference Doc Generator

Generates content for the [Bisq API Reference](https://bisq-network.github.io/slate) site.

## What is bisq-api-reference?

This project's main purpose is to generate the Markdown text for
the [API Reference](https://bisq-network.github.io/slate). It also provides a Java and Python workarea for running API
client example code, and developing new Java and Python clients and bots.

It contains four subprojects:

1. [reference-doc-builder](https://github.com/bisq-network/bisq-api-reference/tree/main/reference-doc-builder) -- The Java
   application that produces the [API Reference](https://bisq-network.github.io/slate) content, from Bisq protobuf
   definition files.
2. [cli-examples](https://github.com/bisq-network/bisq-api-reference/tree/main/cli-examples) -- A folder of bash scripts
   demonstrating how to run API CLI commands. Each script is named for the RPC method call being demonstrated.
3. [java-examples](https://github.com/bisq-network/bisq-api-reference/tree/main/java-examples) -- A Java project
   demonstrating how to call the API from Java gRPC clients. Each class in
   the [bisq.rpccalls](https://github.com/bisq-network/bisq-api-reference/tree/main/java-examples/src/main/java/bisq/rpccalls)
   package is named for the RPC method call being demonstrated.
4. [python-examples](https://github.com/bisq-network/bisq-api-reference/tree/main/python-examples) -- A Python3 project
   demonstrating how to call the API from Python3 gRPC clients. Each class in
   the  [bisq.rpccalls](https://github.com/bisq-network/bisq-api-reference/tree/main/python-examples/bisq/rpccalls) package
   is named for the RPC method call being demonstrated. There are also some simple bot examples in
   the [bisq.bots](https://github.com/bisq-network/bisq-api-reference/tree/main/python-examples/bisq/bots) package.

The RPC method examples are also displayed in the [API Reference](https://bisq-network.github.io/slate). While
navigating the RPC method links in the reference's table of contents on the left side of the page, they appear in the
dark, right side of the page. There is also a copy-to-clipboard icon at the top right of each example.

More details about the subprojects can be found in each subproject's README.
