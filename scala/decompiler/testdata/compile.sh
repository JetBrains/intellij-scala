#!/bin/bash -e

find -regex '.*\.\(sig\|actual\)' -delete

scalaFiles=$(find . -name '*.scala' -not -name '*Wildcard_3*')

export PATH=/opt/scala-2.13.8/bin/:$PATH

echo Compiling .scala files...
scalac -deprecation -Youtline -Ystop-after:pickler -Ypickle-write . $scalaFiles
scalac -deprecation -Youtline -Ystop-after:pickler -Ypickle-write . -Xsource:3 types/Wildcard_3.scala

echo Done.
