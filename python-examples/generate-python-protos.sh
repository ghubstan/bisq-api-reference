#!/bin/bash
#
# Generates Bisq API protobufs from pb.proto and grpc.proto.
#
# Must be run from root project directory `bisq-grpc-api-doc`.  (Set the IDE launcher's working directory).
#
# TODO Use requirement.txt for this step:
# Requirements:
# Install python plugins for protoc.
# python -m pip install grpcio grpcio-tools
# pip3 install mypy-protobuf

# Relative path to directory containing the Bisq .proto files (the protoc compiler input).
export PROTO_PATH="proto"

# The destination directory for the generated Python code (he protoc compiler output).
export PYTHON_PROTO_OUT_PATH="bisq/api"

python3 -m grpc_tools.protoc \
  --proto_path=$PROTO_PATH \
  --python_out=$PYTHON_PROTO_OUT_PATH \
  --grpc_python_out=$PYTHON_PROTO_OUT_PATH  $PROTO_PATH/*.proto
protoc --proto_path=$PROTO_PATH --python_out=$PYTHON_PROTO_OUT_PATH $PROTO_PATH/*.proto

# Hack the internal import statements in the generated python to prepend the `bisq.api` package name.
# See why Google will not fix this:  https://github.com/protocolbuffers/protobuf/issues/1491
sed -i 's/import pb_pb2 as pb__pb2/import bisq.api.pb_pb2 as pb__pb2/g' $PYTHON_PROTO_OUT_PATH/grpc_pb2.py
sed -i 's/import grpc_pb2 as grpc__pb2/import bisq.api.grpc_pb2 as grpc__pb2/g' $PYTHON_PROTO_OUT_PATH/grpc_pb2_grpc.py
