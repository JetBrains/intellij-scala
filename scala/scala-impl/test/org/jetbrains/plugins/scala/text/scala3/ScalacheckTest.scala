package org.jetbrains.plugins.scala.text.scala3

import org.jetbrains.plugins.scala.DependencyManagerBase.RichStr
import org.jetbrains.plugins.scala.text.TextToTextTestBase

class ScalacheckTest extends TextToTextTestBase(
  dependencies = Seq(
    "org.scalacheck" %% "scalacheck" % "1.17.0",
  ),
  packages = Seq("org.scalacheck"),
  minClassCount = 38
)