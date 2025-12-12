package org.jetbrains.plugins.scala.text.scala3

import org.jetbrains.plugins.scala.DependencyManagerBase.RichStr
import org.jetbrains.plugins.scala.text.TextToTextTestBase

class ScalatestTest extends TextToTextTestBase(
  dependencies = Seq(
    "org.scalatest" %% "scalatest" % "3.2.14"
  ),
  packages = Seq("org.scalatest"),
  minClassCount = 660,
  classExceptions = Set(
    "org.scalatest.enablers.InspectorAsserting", // Tuple2 type argument
    "org.scalatest.tools.Framework", // Any
    "org.scalatest.tools.ScalaTestAntTask", // Cannot resolve reference
    "org.scalatest.tools.ScalaTestFramework", // Any
  ),
  withSources = true,
  sourceExceptions = Set(
    "org.scalatest.Assertions", // Multiple `extension`
    "org.scalatest.Suite", //Class[? <: AnyRef] | Class[?]
    "org.scalatest.diagrams.DiagramsMacro", // Cannot resolve x$1.reflect.Term
    "org.scalatest.events.Event", // Object vs Any
    "org.scalatest.exceptions.NotSerializableWrapperException", // case class with Serializable
    "org.scalatest.matchers.AMatcher", // with Object { toString }
    "org.scalatest.matchers.AnMatcher", // with Object { toString }
    "org.scalatest.matchers.Matcher", // T with T, T with Any, Expr[...]
    "org.scalatest.matchers.TypeMatcherMacro", // Cannot resolve x$1.reflect.Term
    "org.scalatest.matchers.dsl.EndWithWord", // with Object { toString }
    "org.scalatest.matchers.dsl.FullyMatchWord", // with Object { toString }
    "org.scalatest.matchers.dsl.IncludeWord", // with Object { toString }
    "org.scalatest.matchers.dsl.MatchPatternWord", // Expr[...]
    "org.scalatest.matchers.dsl.MatcherFactory1", // SC with SC, TC1 vs Nothing, Expr[...]
    "org.scalatest.matchers.dsl.MatcherFactory2", // SC with SC, TC1 vs Nothing, Expr[...]
    "org.scalatest.matchers.dsl.MatcherFactory3", // SC with SC, TC1 vs Nothing, Expr[...]
    "org.scalatest.matchers.dsl.MatcherFactory4", // SC with SC, TC1 vs Nothing, Expr[...]
    "org.scalatest.matchers.dsl.MatcherFactory5", // SC with SC, TC1 vs Nothing, Expr[...]
    "org.scalatest.matchers.dsl.MatcherFactory6", // SC with SC, TC1 vs Nothing, Expr[...]
    "org.scalatest.matchers.dsl.MatcherFactory7", // SC with SC, TC1 vs Nothing, Expr[...]
    "org.scalatest.matchers.dsl.MatcherFactory8", // SC with SC, TC1 vs Nothing, Expr[...]
    "org.scalatest.matchers.dsl.NotWord", // Expr[...]
    "org.scalatest.matchers.dsl.ResultOfNotWordForAny", // Expr[...]
    "org.scalatest.matchers.dsl.StartWithWord", // with Object { toString }
    "org.scalatest.matchers.must.Matchers", // Multiple `extension`
    "org.scalatest.matchers.must.TypeMatcherMacro", // Cannot resolve x$1.reflect.Term
    "org.scalatest.matchers.should.Matchers", // Multiple `extension`
    "org.scalatest.matchers.should.TypeMatcherMacro", // Cannot resolve x$1.reflect.Term
    "org.scalatest.tools.Runner", // Class[? <: AnyRef] | Class[?] (private[scalatest], FromJavaObject)
    "org.scalatest.tools.StringReporter", // Unicode \u001b char
    "org.scalatest.wordspec.AsyncWordSpecLike", // Expr[...]
  )
)