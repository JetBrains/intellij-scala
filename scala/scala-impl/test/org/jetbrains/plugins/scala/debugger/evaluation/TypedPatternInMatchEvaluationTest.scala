package org.jetbrains.plugins.scala
package debugger
package evaluation

import org.junit.experimental.categories.Category

@Category(Array(classOf[DebuggerTests]))
class TypedPatternInMatchEvaluationTest_2_11 extends TypedPatternInMatchEvaluationTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == LatestScalaVersions.Scala_2_11
}

@Category(Array(classOf[DebuggerTests]))
class TypedPatternInMatchEvaluationTest_2_12 extends TypedPatternInMatchEvaluationTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == LatestScalaVersions.Scala_2_12
}

@Category(Array(classOf[DebuggerTests]))
class TypedPatternInMatchEvaluationTest_2_13 extends TypedPatternInMatchEvaluationTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == LatestScalaVersions.Scala_2_13
}

@Category(Array(classOf[DebuggerTests]))
class TypedPatternInMatchEvaluationTest_3_0 extends TypedPatternInMatchEvaluationTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == LatestScalaVersions.Scala_3_0
}

@Category(Array(classOf[DebuggerTests]))
class TypedPatternInMatchEvaluationTest_3_1 extends TypedPatternInMatchEvaluationTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == LatestScalaVersions.Scala_3_1
}

abstract class TypedPatternInMatchEvaluationTestBase extends ExpressionEvaluationTestBase {
  addSourceFile("TypedPatternInMatch.scala",
    s"""object TypedPatternInMatch {
       |  def main(args: Array[String]): Unit = {
       |    val str: Any = "abc"
       |
       |    str match {
       |      case s: String =>
       |        println(s) $breakpoint
       |      case o =>
       |        println(o)
       |    }
       |  }
       |}
       |""".stripMargin.trim
  )

  def testTypedPatternInMatch(): Unit = expressionEvaluationTest() { implicit ctx =>
    "s" evaluatesTo "abc"
  }

  addSourceFile("GuardsCanBeIgnored.scala",
    s"""object GuardsCanBeIgnored {
       |  def main(args: Array[String]): Unit = {
       |    val str: Any = "abc"
       |
       |    str match {
       |      case s: String if s == "abc" =>
       |        println(s) $breakpoint
       |      case o =>
       |        println(o)
       |    }
       |  }
       |}
       |""".stripMargin.trim
  )

  def testGuardsCanBeIgnored(): Unit = expressionEvaluationTest() { implicit ctx =>
    "s" evaluatesTo "abc"
  }

  addSourceFile("TupleNotDestructured.scala",
    s"""object TupleNotDestructured {
       |  def main(args: Array[String]): Unit = {
       |    val tuple: AnyRef = ("abc", 1)
       |
       |    tuple match {
       |      case t: Tuple2[_, _] =>
       |        println(t) $breakpoint
       |      case _ =>
       |        println()
       |    }
       |  }
       |}
       |""".stripMargin.trim
  )

  def testTupleNotDestructured(): Unit = expressionEvaluationTest() { implicit ctx =>
    "t" evaluatesTo "(abc,1)"
  }
}
