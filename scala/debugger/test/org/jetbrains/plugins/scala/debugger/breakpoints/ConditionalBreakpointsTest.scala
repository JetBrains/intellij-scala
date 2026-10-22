package org.jetbrains.plugins.scala
package debugger
package breakpoints

import org.jetbrains.plugins.scala.debugger.evaluation.ExpressionEvaluationTestBase
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

class ConditionalBreakpointsTest_2_13 extends ConditionalBreakpointsTestBase:
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_2_13

class ConditionalBreakpointsTest_3 extends ConditionalBreakpointsTestBase:
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_3

/**
 * The condition of a breakpoint is evaluated on every hit with the same evaluator, which the platform caches per
 * breakpoint request, so these tests exercise the reuse of a built evaluator across evaluations (see SCL-24414).
 */
@RunWith(classOf[JUnit4])
abstract class ConditionalBreakpointsTestBase extends ExpressionEvaluationTestBase:

  addSourceFile("LocalVariableCondition.scala",
    s"""object LocalVariableCondition {
       |  def main(args: Array[String]): Unit = {
       |    var i = 0
       |    while (i < 10) {
       |      i += 1
       |      println(i) $breakpoint ${condition("i % 4 == 3")}
       |    }
       |  }
       |}""".stripMargin.trim)

  @Test
  def localVariableCondition(): Unit =
    expressionEvaluationTest("LocalVariableCondition")(
      implicit ctx => evalEquals("i", "3"),
      implicit ctx => evalEquals("i", "7")
    )

  addSourceFile("MethodCallCondition.scala",
    s"""object MethodCallCondition {
       |  def isInteresting(n: Int): Boolean = n % 4 == 0
       |
       |  def main(args: Array[String]): Unit = {
       |    var i = 0
       |    while (i < 10) {
       |      i += 1
       |      println(i) $breakpoint ${condition("isInteresting(i)")}
       |    }
       |  }
       |}""".stripMargin.trim)

  @Test
  def methodCallCondition(): Unit =
    expressionEvaluationTest("MethodCallCondition")(
      implicit ctx => evalEquals("i", "4"),
      implicit ctx => evalEquals("i", "8")
    )
