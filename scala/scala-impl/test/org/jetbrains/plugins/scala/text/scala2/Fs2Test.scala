package org.jetbrains.plugins.scala.text.scala2

import org.jetbrains.plugins.scala.DependencyManagerBase.RichStr
import org.jetbrains.plugins.scala.text.TextToTextTestBase

class Fs2Test extends TextToTextTestBase(
  dependencies = Seq(
    "co.fs2" %% "fs2-core" % "3.6.1",
  ),
  packages = Seq("fs2"),
  minClassCount = 56,
  classExceptions = Set(
    "fs2.Pull", // Any
  )
)