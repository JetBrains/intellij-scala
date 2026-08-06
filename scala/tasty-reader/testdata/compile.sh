#!/usr/bin/env bash

echo "Script started"

#make the script exit immediately if any simple command returns a non-zero (error) status.
set -e

TEMP_DIR=/tmp/scala3-tasty-reader-test-compile

SCALA_3_0_VERSION="3.0.0"
SCALA_3_6_VERSION="3.6.4"

SCALA_3_0_URL="https://github.com/scala/scala3/releases/download/$SCALA_3_0_VERSION/scala3-$SCALA_3_0_VERSION.tar.gz"
SCALA_3_6_URL="https://github.com/scala/scala3/releases/download/$SCALA_3_6_VERSION/scala3-$SCALA_3_6_VERSION.tar.gz"

download_and_extract() {
    echo "Downloading from $1 to directory $2..."

    local url=$1
    local output_dir=$2
    if [ ! -d "$output_dir" ]; then
        mkdir -p "$output_dir"
        wget "$url" -O "$output_dir/scala.tar.gz"
        tar xzf "$output_dir/scala.tar.gz" -C "$output_dir"
    fi
}

# Remove all existing class files and tasty files
find . \( -name '*.class' -o -name '*.tasty' -o -name '*.actual' \) -type f -exec rm -f {} +

# Filter out some files that are compiled with later, more specific scala version
# TODO: refactor the tests and make the test data separation between scala versions more convenient/transparent/etc...
scalaFiles=$(find . -type f -name '*.scala' ! -name '*NamedContextBounds*' ! -name '*GivenDeferred*' ! -name '*DerivationApi*')

mkdir -p "$TEMP_DIR"
download_and_extract "$SCALA_3_0_URL" "$TEMP_DIR/scala3_0"
download_and_extract "$SCALA_3_6_URL" "$TEMP_DIR/scala3_6"
download_and_extract "$SCALA_3_7_URL" "$TEMP_DIR/scala3_7"

SCALAC_3_0="$TEMP_DIR/scala3_0/scala3-$SCALA_3_0_VERSION/bin/scalac"
SCALAC_3_6="$TEMP_DIR/scala3_6/scala3-$SCALA_3_6_VERSION/bin/scalac"
#export JAVA_HOME=/usr/lib/jvm/default-jdk/

echo "Compiling .scala files... ($SCALA_3_0_VERSION)"
$SCALAC_3_0 -Ykind-projector $scalaFiles

echo "Compiling .scala files... ($SCALA_3_6_VERSION)"
$SCALAC_3_6 -Xkind-projector parameter/NamedContextBounds.scala
$SCALAC_3_6 -Xkind-projector member/GivenDeferred.scala

#$SCALAC_3_6 parameter/NamedContextBounds.tasty -print-tasty -color:never | grep -Po '^[ |\d]{6}: .*' > parameter/NamedContextBounds.tree
#$SCALAC_3_6 member/GivenDeferred.tasty -print-tasty -color:never | grep -Po '^[ |\d]{6}: .*' > member/GivenDeferred.tree
#
#for scalaFile in $scalaFiles; do
#  name="${scalaFile%.*}"
#  tastyFile="$name.tasty"
#  if [ -f $name\$package.tasty ]; then
#    tastyFile=$name\$package.tasty
#  fi
#  treeFile="$name.tree"
#  if [ ! "$treeFile" -nt "$scalaFile" ]; then
#    echo "Parsing $tastyFile..."
#    $SCALAC_3_6 $tastyFile -print-tasty -color:never | grep -Po '^[ |\d]{6}: .*' > $treeFile
#  fi
#  #rm $name.class
#done

echo Done.