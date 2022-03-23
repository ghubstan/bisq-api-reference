#! /bin/bash

# This script must be run from this directory.

# Install python3-venv if necessary.
# For systems using the apt package manager, uncomment the line below, or install the equivalent package for your system.
# sudo apt install python3-venv

# Install pip, then Python setuptools if necessary.
# pip install setuptools

# Download the Bisq protobuf definition files from the Bisq repository.
cd ../proto-downloader
./download-bisq-protos.sh
cd ../python-examples

# Generate gRPC Python protobuf classes.  You can download them from the
# Bisq repo with the proto-downloader/download-bisq-protos.sh script.
echo "Generating gRPC Python service stubs..."
./generate-python-protos.sh

# Set up Python environment in python-examples directory.
echo "Building Python virtual environment in the python-examples directory..."
rm -rf myvenv
python3 -m venv myvenv
source ./myvenv/bin/activate

# Install Python example dependencies.
echo "Installing example code dependencies in virtual environment..."
pip install -r requirements.txt

# Install API example packages in myvenv.
echo "Installing example code packages in virtual environment..."
pip install .
