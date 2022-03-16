#!/bin/bash

echo "Downloading Bisq protobuf files from github to $PROTO_PATH directory."
echo "You may want to skip this step and copy your own local .proto files instead."

set -x
curl -o ./pb.proto https://raw.githubusercontent.com/bisq-network/bisq/master/proto/src/main/proto/pb.proto
curl -o ./grpc.proto https://raw.githubusercontent.com/bisq-network/bisq/master/proto/src/main/proto/grpc.proto

cp -v *.proto ../java-examples/src/main/proto
cp -v *.proto ../python-examples/proto
set +x