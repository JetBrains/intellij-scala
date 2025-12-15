package org.jetbrains.plugins.scala.text.scala3

import org.jetbrains.plugins.scala.DependencyManagerBase.RichStr
import org.jetbrains.plugins.scala.text.TextToTextTestBase

class ScalacticTest extends TextToTextTestBase(
  dependencies = Seq(
    "org.scalactic" %% "scalactic" % "3.2.14",
  ),
  packages = Seq("org.scalactic"),
  minClassCount = 167,
  classExceptions = Set(
    "org.scalactic.Accumulation", // No parentheses in repeated function type
    "org.scalactic.FutureSugar", // No parentheses in repeated function type
    "org.scalactic.TrySugar", // No parentheses for repeated function type
  ),
  withSources = true,
  sourceExceptions = Set(
    "org.scalactic.Every", // :\ | :\\ (in annotation)
    "org.scalactic.anyvals.NonEmptyList", // :\ | :\\ (in annotation)
  )
)