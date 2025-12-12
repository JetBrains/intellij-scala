package org.jetbrains.plugins.scala.text.scala3

import org.jetbrains.plugins.scala.DependencyManagerBase.RichStr
import org.jetbrains.plugins.scala.text.TextToTextTestBase

class CirceTest extends TextToTextTestBase(
  dependencies = Seq(
    "io.circe" %% "circe-core" % "0.14.1",
    "io.circe" %% "circe-generic" % "0.14.1",
    "io.circe" %% "circe-parser" % "0.14.1",
  ),
  packages = Seq("io.circe"),
  minClassCount = 63,
  withSources = true,
  classesWithoutSource = Set(
    // Why are sources not found for these classes?
    "io.circe.ProductCodecs",
    "io.circe.ProductDecoders",
    "io.circe.ProductEncoders",
    "io.circe.TupleDecoders",
    "io.circe.TupleEncoders",
  )
)