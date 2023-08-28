package org.jetbrains.plugins.scala.text.scala3

import org.jetbrains.plugins.scala.DependencyManagerBase.RichStr
import org.jetbrains.plugins.scala.text.TextToTextTestBase

class ScalatestTest extends TextToTextTestBase(
  Seq(
    "org.scalatest" %% "scalatest" % "3.2.14"
  ),
  Seq("org.scalatest"), Set.empty, 660,
  Set(
    "org.scalatest.Assertions", // Inline parameter, anonymous using
    "org.scalatest.CompileMacro", // Given definition
    "org.scalatest.MessageRecordingInformer", // Extra default arguments
    "org.scalatest.Suite", // FromJavaObject
    "org.scalatest.diagrams.Diagrams", // Inline parameter
    "org.scalatest.enablers.InspectorAsserting", // Tuple2 type argument
    "org.scalatest.matchers.CompileMacro", // Given definition
    "org.scalatest.matchers.Matcher", // Inline parameter
    "org.scalatest.matchers.dsl.MatchPatternWord", // Inline parameter
    "org.scalatest.matchers.dsl.MatcherFactory1", // Inline parameter
    "org.scalatest.matchers.dsl.MatcherFactory2", // Inline parameter
    "org.scalatest.matchers.dsl.MatcherFactory3", // Inline parameter
    "org.scalatest.matchers.dsl.MatcherFactory4", // Inline parameter
    "org.scalatest.matchers.dsl.MatcherFactory5", // Inline parameter
    "org.scalatest.matchers.dsl.MatcherFactory6", // Inline parameter
    "org.scalatest.matchers.dsl.MatcherFactory7", // Inline parameter
    "org.scalatest.matchers.dsl.MatcherFactory8", // Inline parameter
    "org.scalatest.matchers.dsl.NotWord", // Inline parameter
    "org.scalatest.matchers.dsl.ResultOfNotWordForAny", // Inline parameter
    "org.scalatest.matchers.must.Matchers", // No this. prefix
    "org.scalatest.matchers.should.Matchers", // No this. prefix
    "org.scalatest.tools.Framework", // Any
    "org.scalatest.tools.Runner", // FromJavaObject
    "org.scalatest.tools.ScalaTestAntTask", // Cannot resolve reference
    "org.scalatest.tools.ScalaTestFramework", // Any
  )
)