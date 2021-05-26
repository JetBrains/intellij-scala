#!/bin/bash

set -e

find -regex '.*\.\(class\|tasty\|actual\)' -delete

scalaFiles=$(find . -name '*.scala')

echo Compiling .scala files...
dotc $scalaFiles

for scalaFile in $scalaFiles; do
  name="${scalaFile%.*}"
  tastyFile="$name.tasty"
  treeFile="$name.tree"
  if [ ! "$treeFile" -nt "$scalaFile" ]; then
    echo "Parsing $tastyFile..."
    dotc $tastyFile -print-tasty -color:never >> $treeFile
  fi
  rm $name.class
done

echo Done.
