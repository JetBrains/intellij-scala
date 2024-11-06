package org.jetbrains.plugins.scala.text.scala3

import org.jetbrains.plugins.scala.DependencyManagerBase.RichStr
import org.jetbrains.plugins.scala.text.TextToTextTestBase

class ScalatestTest extends TextToTextTestBase(
  Seq(
    "org.scalatest" %% "scalatest" % "3.2.14"
  ),
  Seq("org.scalatest"), Set.empty, 660,
  Set(
    "org.scalatest.enablers.InspectorAsserting", // Tuple2 type argument
    "org.scalatest.matchers.must.Matchers", // No this. prefix
    "org.scalatest.matchers.should.Matchers", // No this. prefix
    "org.scalatest.tools.Framework", // Any
    "org.scalatest.tools.ScalaTestAntTask", // Cannot resolve reference
    "org.scalatest.tools.ScalaTestFramework", // Any
  )
)