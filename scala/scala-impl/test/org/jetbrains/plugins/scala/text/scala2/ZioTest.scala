package org.jetbrains.plugins.scala.text.scala2

import org.jetbrains.plugins.scala.DependencyManagerBase.RichStr
import org.jetbrains.plugins.scala.text.TextToTextTestBase

class ZioTest extends TextToTextTestBase(
  dependencies = Seq(
    "dev.zio" %% "zio" % "2.0.2",
    "dev.zio" %% "zio-streams" % "2.0.2",
  ),
  packages = Seq("zio"),
  minClassCount = 226,
  includeScalaReflect = true
)