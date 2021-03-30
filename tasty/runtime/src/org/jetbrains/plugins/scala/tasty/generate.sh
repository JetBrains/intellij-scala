#!/bin/sh
curl https://raw.githubusercontent.com/lampepfl/dotty/3.0.0-RC2/compiler/src/scala/quoted/runtime/impl/printers/SourceCode.scala -o SourceCode.scala
patch SourceCode.scala SourceCode.patch
