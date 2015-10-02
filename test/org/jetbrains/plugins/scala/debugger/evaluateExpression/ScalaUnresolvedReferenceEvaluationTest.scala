package org.jetbrains.plugins.scala.debugger.evaluateExpression

import org.jetbrains.plugins.scala.debugger.{ScalaDebuggerTestCase, ScalaVersion_2_11, ScalaVersion_2_12_M2}

/**
 * User: Alefas
 * Date: 17.10.11
 */

class ScalaUnresolvedReferenceEvaluationTest extends ScalaUnresolvedReferenceEvaluationTestBase with ScalaVersion_2_11
class ScalaUnresolvedReferenceEvaluationTest_2_12_M2 extends ScalaUnresolvedReferenceEvaluationTestBase with ScalaVersion_2_12_M2

abstract class ScalaUnresolvedReferenceEvaluationTestBase extends ScalaDebuggerTestCase {
  def testSimpleDynamicField() {
    addFileToProject("Sample.scala",
      """
      |class A
      |class B extends A {val x = 23}
      |object Sample {
      |  val x: A = new B
      |  def main(args: Array[String]) {
      |    "stop here"
      |  }
      |}
      """.stripMargin.trim()
    )
    addBreakpoint("Sample.scala", 5)
    runDebugger("Sample") {
      waitForBreakpoint()
      evalStartsWith("x.x", "23")
    }
  }
}