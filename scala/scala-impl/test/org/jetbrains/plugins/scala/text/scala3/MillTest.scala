package org.jetbrains.plugins.scala.text.scala3

import org.jetbrains.plugins.scala.DependencyManagerBase.RichStr
import org.jetbrains.plugins.scala.text.TextToTextTestBase

class MillTest extends TextToTextTestBase(
  Seq(
    "com.lihaoyi" %% "mill-main" % "0.13.0-M1-43-b217bc",
  ),
  Seq("mill"), Set.empty, 134,
  Set.empty
)