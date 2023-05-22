package org.jetbrains.plugins.scala.text.scala2

import org.jetbrains.plugins.scala.DependencyManagerBase.RichStr
import org.jetbrains.plugins.scala.text.TextToTextTestBase

class Fs2Test extends TextToTextTestBase(
  Seq(
    "co.fs2" %% "fs2-core" % "3.6.1",
  ),
  Seq("fs2"), Set.empty, 56,
  Set(
    "fs2.Pull", // Any
  )
)