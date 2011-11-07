package org.jetbrains.plugins.scala.debugger.evaluateExpression

/**
 * User: Alexander Podkhalyuzin
 * Date: 07.11.11
 */
class ScalaSyntheticFunctionEvaluationTest extends ScalaDebuggerTestCase {
  def testIsInstanceOf() {
    myFixture.addFileToProject("Sample.scala",
      """
      |object Sample {
      |  class A
      |  class B
      |  def main(args: Array[String]) {
      |    val x = new A
      |    val y = new B
      |    "stop here"
      |  }
      |}
      |
      |object Simple
      """.stripMargin.trim()
    )
    addBreakpoint("Sample.scala", 6)
    runDebugger("Sample") {
      waitForBreakpoint()
      evalEquals("x.isInstanceOf[A]", "true")
      evalEquals("x.isInstanceOf[B]", "false")
      evalEquals("y.isInstanceOf[B]", "true")
      evalEquals("y.isInstanceOf[String]", "false")
      evalEquals("\"\".isInstanceOf[String]", "true")
    }
  }
}