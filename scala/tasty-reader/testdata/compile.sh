#!/usr/bin/env -S bash -e

TEMP_DIR=/tmp/scala3-tasty-reader-test-compile
SCALA3_URL="https://github.com/scala/scala3/releases/download/3.0.0/scala3-3.0.0.tar.gz"
SCALA3_6_URL="https://github.com/scala/scala3/releases/download/3.6.2/scala3-3.6.2.tar.gz"

download_and_extract() {
    local url=$1
    local output_dir=$2
    if [ ! -d "$output_dir" ]; then
        mkdir -p "$output_dir"
        wget "$url" -O "$output_dir/scala.tar.gz"
        tar xzf "$output_dir/scala.tar.gz" -C "$output_dir"
    fi
}

find -regex '.*\.\(class\|tasty\|actual\)' -delete

scalaFiles=$(find . -name '*.scala' -not -name '*NamedContextBounds*' -not -name '*GivenDeferred*')

mkdir -p "$TEMP_DIR"
download_and_extract "$SCALA3_URL" "$TEMP_DIR/scala3"
download_and_extract "$SCALA3_6_URL" "$TEMP_DIR/scala3_6"

SCALAC="$TEMP_DIR/scala3/scala3-3.0.0/bin/scalac"
SCALAC_3_6="$TEMP_DIR/scala3_6/scala3-3.6.2/bin/scalac"

#export JAVA_HOME=/usr/lib/jvm/default-jdk/

echo Compiling .scala files...
$SCALAC -Ykind-projector $scalaFiles

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