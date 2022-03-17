#! /bin/bash

# This script must be run from this directory.

# Install python3-venv if necessary.
# sudo apt install python3-venv

# Install Python setuptools if necessary.
# pip install setuptools

# Set up Python environment in python-examples directory.
rm -rf myvenv
python -m venv myvenv
source ./myvenv/bin/activate

# Install Python example dependencies.
pip install -r requirements.txt

# Install API example packages in myvenv.
pip install .
