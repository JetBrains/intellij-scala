package org.jetbrains.plugins.scala.text.scala2

import org.jetbrains.plugins.scala.DependencyManagerBase.RichStr
import org.jetbrains.plugins.scala.text.TextToTextTestBase

class CirceTest extends TextToTextTestBase(
  dependencies = Seq(
    "io.circe" %% "circe-core" % "0.14.15",
    "io.circe" %% "circe-generic" % "0.14.15",
    "io.circe" %% "circe-parser" % "0.14.15",
  ),
  packages = Seq("io.circe"),
  minClassCount = 79,
  classExceptions = Set(
    "io.circe.Encoder", // export (correct, see ScalaNamesValidator)
    "io.circe.LowPriorityDecoders", // export (correct, see ScalaNamesValidator)
    "io.circe.LowPriorityEncoders", // export (correct, see ScalaNamesValidator)
    "io.circe.generic.AutoDerivation", // export (correct, see ScalaNamesValidator)
    "io.circe.generic.Deriver", // Cannot resolve reference
    "io.circe.generic.GenericJsonCodecMacros", // Cannot resolve reference
    "io.circe.generic.util.macros.DerivationMacros", // Cannot resolve reference
    "io.circe.generic.util.macros.ExportMacros", // Cannot resolve reference
    "io.circe.generic.util.macros.JsonCodecMacros", // Cannot resolve reference
  )
)