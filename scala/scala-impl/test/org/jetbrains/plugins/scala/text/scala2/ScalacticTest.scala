package org.jetbrains.plugins.scala.text.scala2

import org.jetbrains.plugins.scala.DependencyManagerBase.RichStr
import org.jetbrains.plugins.scala.text.TextToTextTestBase

class ScalacticTest extends TextToTextTestBase(
  Seq(
    "org.scalactic" %% "scalactic" % "3.2.14",
  ),
  Seq("org.scalactic"), Set.empty, 170,
  Set(
    "org.scalactic.Accumulation", // No parentheses for repeated function type
    "org.scalactic.FutureSugar", // No parentheses for repeated function type
    "org.scalactic.TrySugar", // No parentheses for repeated function type
    "org.scalactic.source.TypeInfoMacro", // Cannot resolve reference
  ),
  includeScalaReflect = true
)