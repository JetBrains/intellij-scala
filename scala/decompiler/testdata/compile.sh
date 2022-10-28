#!/bin/bash -e

find -regex '.*\.\(sig\|actual\)' -delete

scalaFiles=$(find . -name '*.scala')

export PATH=/opt/scala-2.13.8/bin/:$PATH

echo Compiling .scala files...
scalac -deprecation -Youtline -Ystop-after:pickler -Ypickle-write . $scalaFiles

echo Done.
