package org.jetbrains.plugins.scala.debugger.evaluation

import org.jetbrains.plugins.scala.ScalaVersion

class ExpressionCompilerErrorTest extends ExpressionEvaluationTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_3

  addSourceFile("expression/eval/main.scala",
    s"""package expression.eval
       |
       |case class B(i: Int)
       |case class A(children: Seq[B])
       |
       |@main
       |def main(): Unit =
       |  val a = A(Seq.empty)
       |  println("Hello, world!") $breakpoint
       |""".stripMargin)

  def testExpressionCompilerError(): Unit = {
    expressionEvaluationTest("expression.eval.main") { implicit ctx =>
      evalFailsWith("a.children.map(i => i + 1)", "Expression compilation failed: ")
      evalFailsWithContains("""3 * "123"""", "Expression compilation failed: ")
    }
  }
}
