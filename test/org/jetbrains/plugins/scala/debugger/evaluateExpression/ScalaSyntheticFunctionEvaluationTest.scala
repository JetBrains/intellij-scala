package org.jetbrains.plugins.scala.debugger.evaluateExpression

import org.jetbrains.plugins.scala.debugger.ScalaDebuggerTestCase

/**
 * User: Alexander Podkhalyuzin
 * Date: 07.11.11
 */
class ScalaSyntheticFunctionEvaluationTest extends ScalaDebuggerTestCase {
  def testIsInstanceOf() {
    addFileToProject("Sample.scala",
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
      evalEquals("1 < 1", "false")
      evalEquals("1 <= 1", "true")
      evalEquals("1 + 2", "3")
      evalEquals("3 - 1.5.toInt", "2")
      evalEquals("false ^ true", "true")
      evalEquals("!false", "true")
      evalEquals("false | false", "false")
      evalEquals("1 / 2", "0")
      evalEquals("1 / 2.", "0.5")
      evalEquals("5 % 2", "1")
      evalEquals("1 << 2", "4")
      evalEquals("\"1\" + 1", "11")
      evalEquals("List[Int](1, 2)", "List(1, 2)")
      evalEquals("List(1, 2)", "List(1, 2)")
    }
  }

  def testConditionalOperators(): Unit = {
    addFileToProject("Sample.scala",
      """
        |object Sample {
        |  def fail: Boolean = throw new Exception("fail!")
        |  def main(args: Array[String]) {
        |     val tr = true
        |     val fls = false
        |    "stop here"
        |  }
        |}
      """.stripMargin.trim()
    )
    addBreakpoint("Sample.scala", 5)
    runDebugger("Sample") {
      waitForBreakpoint()
      evalEquals("tr || fail", "true")
      evalEquals("fls && fail", "false")
      evalEquals("fls || tr", "true")
      evalEquals("tr && fls", "false")
    }
  }
}