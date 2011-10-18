package org.jetbrains.plugins.scala.debugger.evaluateExpression

/**
 * User: Alefas
 * Date: 17.10.11
 */

class ScalaUnresolvedReferenceEvaluationTest extends ScalaDebuggerTestCase {
  def testSimpleDynamicField() {
    myFixture.addFileToProject("Sample.scala",
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