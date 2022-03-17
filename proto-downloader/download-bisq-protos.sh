#!/bin/bash

echo "Downloading Bisq protobuf files from github to $PROTO_PATH directory."
echo "You may want to skip this step and copy your own local .proto files instead."

# Download the Bisq master branch's protobuf definition files.
curl -o ./pb.proto https://raw.githubusercontent.com/bisq-network/bisq/master/proto/src/main/proto/pb.proto
curl -o ./grpc.proto https://raw.githubusercontent.com/bisq-network/bisq/master/proto/src/main/proto/grpc.proto

# Copy the proto files to the Java and Python example projects.
cp -v *.proto ../java-examples/src/main/proto
cp -v *.proto ../python-examples/proto
