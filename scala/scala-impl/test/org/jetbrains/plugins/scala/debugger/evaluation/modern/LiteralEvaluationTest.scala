package org.jetbrains.plugins.scala.debugger.evaluation.modern

import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}

class LiteralEvaluationTest extends ExpressionEvaluationTestBase {

  override protected def supportedIn(version: ScalaVersion): Boolean = version == LatestScalaVersions.Scala_3

  addSourceFile("Literal.scala",
    s"""@main
       |def literal(): Unit =
       |  println() $breakpoint
     """.stripMargin
  )

  def testLiteral(): Unit = expressionEvaluationTest("literal") { implicit ctx =>
    evalEquals("true", "true")
    evalEquals("false", "false")
    evalEquals("'c'", "c")
    evalEquals("567.8", "567.8")
    evalEquals("123.4f", "123.4")
    evalEquals("123", "123")
    evalEquals("123L", "123")
    evalEquals(""""abc"""", "abc")
    evalEquals("()", "undefined")
  }
}
