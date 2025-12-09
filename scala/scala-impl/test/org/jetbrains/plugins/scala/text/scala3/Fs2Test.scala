package org.jetbrains.plugins.scala.text.scala3

import org.jetbrains.plugins.scala.DependencyManagerBase.RichStr
import org.jetbrains.plugins.scala.text.TextToTextTestBase

class Fs2Test extends TextToTextTestBase(
  dependencies = Seq(
    "co.fs2" %% "fs2-core" % "3.6.1",
  ),
  packages = Seq("fs2"),
  minClassCount = 54,
  classExceptions = Set(
    "fs2.ChunkCompanionPlatform", // IArray is Any
    "fs2.ChunkPlatform", // IArray is Any
    "fs2.CollectorPlatform", // type.Aux
    "fs2.Pull", // fs2.Pull.Terminal is Any
  ),
  withSources = true,
  sourceExceptions = Set(
    "fs2.Stream", // private type ZipWithLeft
  )
)