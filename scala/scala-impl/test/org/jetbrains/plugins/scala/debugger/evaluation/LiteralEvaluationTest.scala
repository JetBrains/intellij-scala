package org.jetbrains.plugins.scala
package debugger
package evaluation

import org.junit.experimental.categories.Category

@Category(Array(classOf[DebuggerTests]))
class LiteralEvaluationTest_2_11 extends LiteralEvaluationTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == LatestScalaVersions.Scala_2_11
}

@Category(Array(classOf[DebuggerTests]))
class LiteralEvaluationTest_2_12 extends LiteralEvaluationTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == LatestScalaVersions.Scala_2_12
}

@Category(Array(classOf[DebuggerTests]))
class LiteralEvaluationTest_2_13 extends LiteralEvaluationTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == LatestScalaVersions.Scala_2_13
}

@Category(Array(classOf[DebuggerTests]))
class LiteralEvaluationTest_3_0 extends LiteralEvaluationTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == LatestScalaVersions.Scala_3_0
}

@Category(Array(classOf[DebuggerTests]))
class LiteralEvaluationTest_3_1 extends LiteralEvaluationTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == LatestScalaVersions.Scala_3_1
}

abstract class LiteralEvaluationTestBase extends ExpressionEvaluationTestBase {
  addSourceFile("Literal.scala",
    s"""object Literal {
       |  def main(args: Array[String]): Unit = {
       |    println() $breakpoint
       |  }
       |}
       |""".stripMargin.trim
  )

  def testLiteral(): Unit = expressionEvaluationTest() { implicit ctx =>
    "true" evaluatesTo true
    "false" evaluatesTo false
    """'a'""" evaluatesTo 'a'
    "1.0" evaluatesTo 1.0
    "1.0f" evaluatesTo 1.0f
    "1" evaluatesTo 1
    "1L" evaluatesTo 1L
    """"string"""" evaluatesTo "string"
  }
}
