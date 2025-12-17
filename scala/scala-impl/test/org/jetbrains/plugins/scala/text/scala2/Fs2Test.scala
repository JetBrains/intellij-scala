package org.jetbrains.plugins.scala.text.scala2

import org.jetbrains.plugins.scala.DependencyManagerBase.RichStr
import org.jetbrains.plugins.scala.text.TextToTextTestBase

class Fs2Test extends TextToTextTestBase(
  dependencies = Seq(
    "co.fs2" %% "fs2-core" % "3.12.2",
  ),
  packages = Seq("fs2"),
  minClassCount = 72,
  classExceptions = Set(
    "fs2.Pull", // Any
    "fs2.interop.flow.StreamSubscriber", // Cannot resolve fs2.interop.flow.StreamSubscriber (in private object)
  )
)