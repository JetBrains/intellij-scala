package org.jetbrains.plugins.scala.text.scala2

import org.jetbrains.plugins.scala.DependencyManagerBase.RichStr
import org.jetbrains.plugins.scala.text.TextToTextTestBase

class ScalacheckTest extends TextToTextTestBase(
  Seq(
    "org.scalacheck" %% "scalacheck" % "1.17.0",
  ),
  Seq("org.scalacheck"), Set.empty, 38,
  Set.empty
)