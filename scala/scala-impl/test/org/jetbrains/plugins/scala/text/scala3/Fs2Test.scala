package org.jetbrains.plugins.scala.text.scala3

import org.jetbrains.plugins.scala.DependencyManagerBase.RichStr
import org.jetbrains.plugins.scala.text.TextToTextTestBase

class Fs2Test extends TextToTextTestBase(
  Seq(
    "co.fs2" %% "fs2-core" % "3.6.1",
  ),
  Seq("fs2"), Set.empty, 54,
  Set(
    "fs2.Chunk", // Extra default arguments
    "fs2.ChunkCompanionPlatform", // IArray is Any
    "fs2.ChunkPlatform", // IArray is Any
    "fs2.CollectorPlatform", // type.Aux
    "fs2.Pull", // fs2.Pull.Terminal is Any
  )
)