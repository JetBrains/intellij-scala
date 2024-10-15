package org.jetbrains.plugins.scala.text.scala2

import org.jetbrains.plugins.scala.DependencyManagerBase.RichStr
import org.jetbrains.plugins.scala.text.TextToTextTestBase

class CirceTest extends TextToTextTestBase(
  Seq(
    "io.circe" %% "circe-core" % "0.14.1",
    "io.circe" %% "circe-generic" % "0.14.1",
    "io.circe" %% "circe-parser" % "0.14.1",
  ),
  Seq("io.circe"), Set.empty, 73,
  Set(
    "io.circe.Encoder", // export
    "io.circe.LowPriorityDecoders", // export
    "io.circe.LowPriorityEncoders", // export
    "io.circe.generic.AutoDerivation", // export
    "io.circe.generic.Deriver", // Cannot resolve reference
    "io.circe.generic.GenericJsonCodecMacros", // Cannot resolve reference
    "io.circe.generic.util.macros.DerivationMacros", // Cannot resolve reference
    "io.circe.generic.util.macros.ExportMacros", // Cannot resolve reference
    "io.circe.generic.util.macros.JsonCodecMacros", // Cannot resolve reference
  )
)