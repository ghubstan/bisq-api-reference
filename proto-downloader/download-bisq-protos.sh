#!/bin/bash

# Download Bisq's protobuf definition files.
# Change the REPO_OWNER and REPO_BRANCH vars if you (developer) need to download from an alternative repo and branch.

REPO_OWNER="bisq-network"
REPO_BRANCH="master"
REPO_PROTO_DIR_URL="https://raw.githubusercontent.com/$REPO_OWNER/bisq/$REPO_BRANCH/proto/src/main/proto"

# Get the script directory (relative to the current directory), cd into the directory, use pwd to get the absolute path.
export SCRIPT_DIR=$(cd $(dirname "${BASH_SOURCE[0]}") && pwd)
cd "$SCRIPT_DIR"

echo "Downloading Bisq protobuf files from $REPO_PROTO_DIR_URL to $PROTO_PATH directory..."
echo "You can skip this step and copy your own local .proto files instead."

curl -o ./pb.proto "$REPO_PROTO_DIR_URL"/pb.proto
curl -o ./grpc.proto "$REPO_PROTO_DIR_URL"/grpc.proto

echo "Copying proto files to the Java examples project."
cp -v *.proto ../java-examples/src/main/proto

echo "Moving proto files to the Python examples project;  removing .proto files in this download directory."
mv -v *.proto ../python-examples/proto
