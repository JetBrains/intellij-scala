#!/bin/bash -e

find -regex '.*\.\(class\|tasty\|actual\)' -delete

scalaFiles=$(find . -name '*.scala')

export PATH=/opt/scala3-3.0.0/bin/:$PATH

echo Compiling .scala files...
scalac -Ykind-projector $scalaFiles

for scalaFile in $scalaFiles; do
  name="${scalaFile%.*}"
  tastyFile="$name.tasty"
  if [ -f $name\$package.tasty ]; then
    tastyFile=$name\$package.tasty
  fi
  treeFile="$name.tree"
  if [ ! "$treeFile" -nt "$scalaFile" ]; then
    echo "Parsing $tastyFile..."
    scalac $tastyFile -print-tasty -color:never | grep -Po '^[ |\d]{6}: .*' > $treeFile
  fi
  #rm $name.class
done

echo Done.
