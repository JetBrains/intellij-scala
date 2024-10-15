package org.jetbrains.plugins.scala.text.scala3

import org.jetbrains.plugins.scala.DependencyManagerBase.RichStr
import org.jetbrains.plugins.scala.text.TextToTextTestBase

class CirceTest extends TextToTextTestBase(
  Seq(
    "io.circe" %% "circe-core" % "0.14.1",
    "io.circe" %% "circe-generic" % "0.14.1",
    "io.circe" %% "circe-parser" % "0.14.1",
  ),
  Seq("io.circe"), Set.empty, 63,
  Set(
    "io.circe.Encoder", // export
    "io.circe.Exported", // export
    "io.circe.LowPriorityDecoders", // export
    "io.circe.LowPriorityEncoders", // export
    "io.circe.generic.AutoDerivation", // export
  )
)