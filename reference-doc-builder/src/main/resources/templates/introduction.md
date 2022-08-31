# Introduction

Welcome to the Bisq gRPC API reference documentation for Bisq
Daemon ([master branch](https://github.com/bisq-network/bisq/tree/master)).

You can use this API to access local Bisq daemon API endpoints, which provide a subset of the Bisq Desktop application's
feature set:  check balances, transfer BTC and BSQ, create payment accounts, view offers, create and take offers, and
execute trades.

The Bisq API is based on the gRPC framework, and any supported gRPC language binding can be used to call Bisq API
endpoints. This document provides code examples for language bindings in Java and Python, plus bisq-cli (CLI) command
examples. The code examples are viewable in the dark area to the right, and you can switch between programming language
examples with the tabs on the top right.

The original *.proto files from which the gRPC documentation was generated can be found here:

* [pb.proto](https://github.com/bisq-network/bisq/tree/master/proto/src/main/proto/pb.proto)
* [grpc.proto](https://github.com/bisq-network/bisq/tree/master/proto/src/main/proto/grpc.proto)

This API documentation was created with [Slate](https://github.com/slatedocs/slate).

