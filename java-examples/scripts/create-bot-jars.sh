#! /bin/bash

########################################################################################################################
# Create a runnable jar files from the java-examples' Gradle distribution tarball.
#
# Usage:  $ ./create-bot-jars.sh    0.0.1-SNAPSHOT
#
# Should be called from this directory.
########################################################################################################################

VERSION="$1"
if [[ -z "$VERSION" ]]; then
   VERSION="0.0.1-SNAPSHOT"
fi

# Get the script directory (relative to the current directory), cd into the directory, use pwd to get the absolute path.
export SCRIPT_DIR=$(cd $(dirname "${BASH_SOURCE[0]}") && pwd)
cd "$SCRIPT_DIR"

export GRADLE_DIST_NAME="java-examples-$VERSION"
export GRADLE_DIST_TARBALL="$GRADLE_DIST_NAME.tar"
export GRADLE_DIST_PATH="../build/distributions/$GRADLE_DIST_TARBALL"
echo "Build runnable bot jars from $GRADLE_DIST_PATH"
ls -l "$GRADLE_DIST_PATH"

extractdistribution() {
  # Copy the build's distribution tarball to this directory.
	cp -v "$GRADLE_DIST_PATH" .
	# Extract the tarball content.
	rm -rf "$GRADLE_DIST_NAME"
	tar -xf "$GRADLE_DIST_TARBALL"

	cd "$SCRIPT_DIR/$GRADLE_DIST_NAME"
	# TODO might want to just delete files in bin directory, and put java jar cmd scripts in them?
	# Delete the bin directory.
	rm -rf bin
  echo "Run $ ls -l lib (lib dir should contain all distribution jars)"
  ls -l lib

  cd "$SCRIPT_DIR"
}

extractdistribution

./create-runnable-jar.sh "$GRADLE_DIST_NAME" bisq.bots.TakeBestPricedOfferToBuyBtc
./create-runnable-jar.sh "$GRADLE_DIST_NAME" bisq.bots.TakeBestPricedOfferToSellBtc

./create-runnable-jar.sh "$GRADLE_DIST_NAME" bisq.bots.TakeBestPricedOfferToBuyBsq
./create-runnable-jar.sh "$GRADLE_DIST_NAME" bisq.bots.TakeBestPricedOfferToSellBsq

rm -r "$GRADLE_DIST_TARBALL"
echo "Done"
