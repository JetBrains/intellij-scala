package org.jetbrains.plugins.scala.text.scala2

import org.jetbrains.plugins.scala.DependencyManagerBase.RichStr
import org.jetbrains.plugins.scala.text.TextToTextTestBase

class ScalacticTest extends TextToTextTestBase(
  dependencies = Seq(
    "org.scalactic" %% "scalactic" % "3.2.14",
  ),
  packages = Seq("org.scalactic"),
  minClassCount = 170,
  classExceptions = Set(
    "org.scalactic.Accumulation", // No parentheses for repeated function type
    "org.scalactic.FutureSugar", // No parentheses for repeated function type
    "org.scalactic.TrySugar", // No parentheses for repeated function type
    "org.scalactic.source.TypeInfoMacro", // Cannot resolve reference
  ),
  includeScalaReflect = true
)