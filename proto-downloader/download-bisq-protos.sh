#!/bin/bash

echo "Downloading Bisq protobuf files from github to $PROTO_PATH directory."
echo "You may want to skip this step and copy your own local .proto files instead."

# Download the Bisq master branch's protobuf definition files.
curl -o ./pb.proto https://raw.githubusercontent.com/bisq-network/bisq/master/proto/src/main/proto/pb.proto
curl -o ./grpc.proto https://raw.githubusercontent.com/bisq-network/bisq/master/proto/src/main/proto/grpc.proto

echo "Copying proto files to the Java examples project."
cp -v *.proto ../java-examples/src/main/proto

echo "Moving proto files to the Python examples project;  removing .proto files in this download directory."
mv -v *.proto ../python-examples/proto
