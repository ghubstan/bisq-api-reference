#! /bin/bash

########################################################################################################################
# Create a runnable jar file containing a single class, and a manifest defining the complete classpath.
#
# Usage:
#     `$ ./create-runnable-jar.sh  java-examples-0.0.1-SNAPSHOT  bisq.bots.TakeBestPricedOfferToSellBtc`
#
# Should be called from create-bot-jars.sh, with extracts and arranges the Gradle distribution for this script.
########################################################################################################################

GRADLE_DIST_NAME="$1"
if [[ -z "$GRADLE_DIST_NAME" ]]; then
  echo "Gradle distribution name argument required."
  exit
fi

FULLY_QUALIFIED_CLASSNAME="$2"
if [[ -z "$FULLY_QUALIFIED_CLASSNAME" ]]; then
  echo "Fully qualified classname argument required."
  exit
fi

tolowercasekebabname() {
  STRING="$1"
  # Say we have STRING=FuBarFooBar.  We want to return fu-bar-foo-bar.
  # 1.  echo "$(echo "$STRING" | sed 's/[A-Z]\+/-&/g')" RETURNS "-Fu-Bar-Foo-Bar".
  MESSY_KEBAB="$(echo "$STRING" | sed 's/[A-Z]\+/-&/g')"
  # 2. Strip the leading "-".
  CLEAN_KEBAB="$(echo "$MESSY_KEBAB" | sed 's/-//1')"
  # 3. Lowercase everything using 'tr'.
  LOWERCASE_KEBAB=$(echo "$CLEAN_KEBAB" |  tr '[:upper:]' '[:lower:]')
  echo "$LOWERCASE_KEBAB"
}

getsimpleclassname() {
  FULLY_QUALIFIED_CLASSNAME="$1"
  SIMPLE_CLASSNAME=$(echo "${FULLY_QUALIFIED_CLASSNAME##*.}")
  echo "$SIMPLE_CLASSNAME"
}

getmainclassfilepath() {
  FULLY_QUALIFIED_CLASSNAME="$1"
  FILE_PATH=$(echo "$FULLY_QUALIFIED_CLASSNAME" |  tr '.' '/')
  echo "$FILE_PATH.class"
}

writemanifest() {
  # Make the cli.jar runnable, and define its dependencies in a MANIFEST.MF file.
  echo "Manifest-Version: 1.0" > MANIFEST.txt
	echo "Main-Class: $FULLY_QUALIFIED_CLASSNAME" >> MANIFEST.txt
	printf "Class-Path:" >> MANIFEST.txt
	for file in lib/*
	do
	  # Each new line in the classpath must be preceded by two spaces.
	  printf "  %s\n" "$file" >> MANIFEST.txt
	done
	echo "Manifest for runnable jar:"
	cat MANIFEST.txt
}

SIMPLE_CLASSNAME=$(getsimpleclassname "$FULLY_QUALIFIED_CLASSNAME")
# echo "SIMPLE_CLASSNAME = $SIMPLE_CLASSNAME"

JAR_BASENAME=$(tolowercasekebabname "$SIMPLE_CLASSNAME")
# echo "JAR_BASENAME = $JAR_BASENAME"

echo "Build runnable $JAR_BASENAME.jar containing only $FULLY_QUALIFIED_CLASSNAME"

cd $GRADLE_DIST_NAME

# Build MANIFEST.MF with a Main-Class.
writemanifest

# Create $JAR_BASENAME.jar with the NO class files.
jar --verbose --create --file "$JAR_BASENAME.jar" --manifest MANIFEST.txt

# Delete generated manifest.
rm MANIFEST.txt

# Get the relative file path for the Main-Class to be added to the new jar.
MAINCLASS_FILE_PATH=$(getmainclassfilepath "$FULLY_QUALIFIED_CLASSNAME")
# echo "MAINCLASS_FILE_PATH = $MAINCLASS_FILE_PATH"

# Extract the Main-Class from the distribution jar, to the current working directory.
jar xfv "lib/$GRADLE_DIST_NAME.jar" "$MAINCLASS_FILE_PATH" "$SIMPLE_CLASSNAME.properties"
echo "Extracted $SIMPLE_CLASSNAME.class:"
ls -l "bisq/bots/$SIMPLE_CLASSNAME.class"
mv "$SIMPLE_CLASSNAME.properties" "$JAR_BASENAME.conf"
echo "Extracted $SIMPLE_CLASSNAME.properties and renamed it $JAR_BASENAME.conf"
ls -l "$JAR_BASENAME.conf"

# Now it can be added to the empty jar with the correct path.
jar uf "$JAR_BASENAME.jar" "$MAINCLASS_FILE_PATH"
# Remove workarea.
rm -rf bisq

echo "Runnable $JAR_BASENAME.jar is ready to use."
echo "Usage:  $ java -jar $JAR_BASENAME.jar --password=xyz --conf=$JAR_BASENAME.conf --dryrun=true"
