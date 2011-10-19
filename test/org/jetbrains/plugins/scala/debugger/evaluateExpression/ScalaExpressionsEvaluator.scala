package org.jetbrains.plugins.scala.debugger.evaluateExpression

/**
 * User: Alefas
 * Date: 19.10.11
 */

class ScalaExpressionsEvaluator extends ScalaDebuggerTestCase {
  def testThis() {
    myFixture.addFileToProject("Sample.scala",
      """
      |object Sample {
      |  def main(args: Array[String]) {
      |    class This {
      |      val x = 1
      |      def foo() {
      |       "stop here"
      |      }
      |    }
      |    new This().foo()
      |  }
      |}
      """.stripMargin.trim()
    )
    addBreakpoint("Sample.scala", 5)
    runDebugger("Sample") {
      waitForBreakpoint()
      evalEquals("this.x", "1")
    }
  }

  def testPrefixedThis() {
    myFixture.addFileToProject("Sample.scala",
      """
      |object Sample {
      |  def main(args: Array[String]) {
      |    class This {
      |      val x = 1
      |      def foo() {
      |        val runnable = new Runnable {
      |          def run() {
      |            val x = () => {
      |             This.this.x //to have This.this in scope
      |             "stop here"
      |            }
      |            x()
      |          }
      |        }
      |        runnable.run()
      |      }
      |    }
      |    new This().foo()
      |  }
      |}
      """.stripMargin.trim()
    )
    addBreakpoint("Sample.scala", 9)
    runDebugger("Sample") {
      waitForBreakpoint()
      evalEquals("This.this.x", "1")
    }
  }

  def testPostfix() {
    myFixture.addFileToProject("Sample.scala",
      """
      |object Sample {
      |  def main(args: Array[String]) {
      |    object x {val x = 1}
      |    x
      |    "stop here"
      |  }
      |}
      """.stripMargin.trim()
    )
    addBreakpoint("Sample.scala", 4)
    runDebugger("Sample") {
      waitForBreakpoint()
      evalEquals("x x", "1")
    }
  }
}