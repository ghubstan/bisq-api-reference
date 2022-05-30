#! /bin/bash

# This script must be run from this directory.

####################################################################################
# Install python3-venv if necessary.
####################################################################################

# If using the apt package manager on Ubuntu, uncomment line below to install venv.
# sudo apt install python3-venv

# If using the dnf package manager on Fedora, you should already have venv installed
# with Python3.

####################################################################################
# Install python3 tkinter if necessary.
####################################################################################

# If using the apt package manager on Ubuntu, uncomment line below to install tkinter.
# sudo apt-get install python3-tk

# If using the dnf package manager on Fedora, uncomment line below to install tkinter.
# sudo dnf install python3-tkinter

# If using brew on Mac OS, uncomment line below to install tkinter.
# brew install python-tk

####################################################################################
# Install pip, then Python setuptools if necessary.
####################################################################################
# pip install setuptools

# Download the Bisq protobuf definition files from the Bisq repository.
cd ../proto-downloader
./download-bisq-protos.sh
cd ../python-examples

# Set up Python environment in python-examples directory.
echo "Building Python virtual environment in the python-examples directory..."
rm -rf myvenv
python3 -m venv myvenv
source ./myvenv/bin/activate

# Upgrade myvenv's pip
python3 -m pip install --upgrade pip

# Install Python example dependencies.
echo "Installing example code dependencies in virtual environment..."
pip install -r requirements.txt

# Generate gRPC Python protobuf classes.  You can download them from the
# Bisq repo with the proto-downloader/download-bisq-protos.sh script.
echo "Generating gRPC Python service stubs..."
./generate-python-protos.sh

# Install API example packages in myvenv.
echo "Installing example code packages in virtual environment..."
pip install .

