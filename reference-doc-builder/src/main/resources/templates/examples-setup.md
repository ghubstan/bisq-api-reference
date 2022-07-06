# Running Example Code

Be careful about running any example that could affect your mainnet wallet.  You might want to send `sendbsq`, 
`sendbtc`, `createoffer`, and `takeoffer` requests to an API daemon connected to a local regtest network before trying 
them on mainnet.

There is a convenient way to run a regtest bitcoin-core daemon, a Bisq seed node, an arbitration node, and two regtest 
API daemons called Alice (listening on port 9998), and Bob (listening on port 9999). The Bob and Alice daemons will 
have regtest wallets containing 10 BTC. Bob's BSQ wallet will also be set up with 1500000 BSQ, Alice's with 1000000 BSQ. 
These two API daemons can simulate trading over the local regtest network.  Running a local, Bisq regtest network is 
useful if you want to develop your own API bots.

See
the [Bisq API Beta Testing Guide](https://github.com/bisq-network/bisq/blob/master/apitest/docs/api-beta-test-guide.md)
for instructions on how to get the Bisq API test harness up and running.

## CLI Examples

The API CLI is included in Bisq; no protobuf code generation tasks are required to run the CLI examples in this
document.

The only requirements are:

- A running, local API daemon, preferably the test harness described in
  the [Bisq API Beta Testing Guide](https://github.com/bisq-network/bisq/blob/master/apitest/docs/api-beta-test-guide.md)
- A terminal open in the Bisq source project's root directory

## Java Examples

Running Java examples requires:

- A running, local API daemon, preferably the test harness described in
  the [Bisq API Beta Testing Guide](https://github.com/bisq-network/bisq/blob/master/apitest/docs/api-beta-test-guide.md)
- Downloading Bisq protobuf definition files
- Generating protobuf and gRPC service stubs using the the [protoc](https://grpc.io/docs/protoc-installation/) compiler,
  with the [protoc-gen-grpc-java](https://github.com/grpc/grpc-java) plugin.

### Download Bisq .proto files and generate API's gPRC service stubs

See [Generating Protobuf Code](https://github.com/bisq-network/bisq-api-reference/blob/main/java-examples/README.md#generating-protobuf-code)
in the java-examples README.

### Do it yourself:  manually generate Bisq API protobuf stubs with protoc compiler and grpc-java plugin

If you prefer to generate the Java protos from a terminal, you can compile
the [protoc gen-java](https://github.com/grpc/grpc-java/blob/master/COMPILING.md) binary from source, or manually
download the [binary](https://repo1.maven.org/maven2/io/grpc/protoc-gen-grpc-java/) to your system `PATH`, and
run `protoc` with the appropriate options:

    `$ protoc --plugin=protoc-gen-grpc-java=$GEN_GRPC_JAVA_BINARY_PATH \`</br>
        `--grpc-java_out=$JAVA_PROTO_OUT_PATH \`</br>
        `--proto_path=$PROTO_PATH \`</br>
          `$PROTO_PATH/*.proto`</br>

_Note:  My attempts to compile the protoc gen-java plugin on my own platform were unsuccessful. You may have better luck
or time to resolve platform specific build issues._

## Python Examples

Running Python examples requires:

- A running, local API daemon, preferably the test harness described in
  the [Bisq API Beta Testing Guide](https://github.com/bisq-network/bisq/blob/master/apitest/docs/api-beta-test-guide.md)
- Downloading Bisq protobuf definition files
- Generating protobuf and gRPC service stubs using the `protoc` compiler, with two additional Python protobuf and grpc
  plugins.

You can download the Bisq protobuf (.proto) files by running:

    `proto-downloader/download-bisq-protos.sh`

You can build Python .proto stubs, install Python example dependencies, and package the examples by running:

    `python-examples/run-setup.sh`
