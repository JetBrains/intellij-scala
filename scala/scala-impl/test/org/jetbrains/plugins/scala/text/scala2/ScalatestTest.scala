package org.jetbrains.plugins.scala.text.scala2

import org.jetbrains.plugins.scala.DependencyManagerBase.RichStr
import org.jetbrains.plugins.scala.text.TextToTextTestBase

class ScalatestTest extends TextToTextTestBase(
  Seq(
    "org.scalatest" %% "scalatest" % "3.2.14"
  ),
  Seq("org.scalatest"), Set.empty, 677,
  Set(
    "org.scalatest.Suite", // Existential type
    "org.scalatest.enablers.InspectorAsserting", // Tuple2 type argument
    "org.scalatest.tools.Framework", // Any
    "org.scalatest.tools.Runner", // Existential type
    "org.scalatest.tools.ScalaTestAntTask", // Cannot resolve reference
    "org.scalatest.tools.ScalaTestFramework", // Any
  ),
  includeScalaReflect = true
)