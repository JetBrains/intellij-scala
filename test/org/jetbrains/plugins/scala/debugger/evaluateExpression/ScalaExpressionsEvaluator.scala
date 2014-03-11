package org.jetbrains.plugins.scala.debugger.evaluateExpression

import org.jetbrains.plugins.scala.debugger.ScalaDebuggerTestCase

/**
 * User: Alefas
 * Date: 19.10.11
 */

class ScalaExpressionsEvaluator extends ScalaDebuggerTestCase {
  def testPrefixUnary() {
    addFileToProject("Sample.scala",
      """
      |object Sample {
      |  class U {
      |    def unary_!(): Boolean = false
      |  }
      |  def main(args: Array[String]) {
      |    val u = new U
      |    "stop here"
      |  }
      |}
      """.stripMargin.trim()
    )
    addBreakpoint("Sample.scala", 6)
    runDebugger("Sample") {
      waitForBreakpoint()
      evalEquals("!u", "false")
    }
  }

  def testTupleExpr() {
    addFileToProject("Sample.scala",
      """
      |object Sample {
      |  def main(args: Array[String]) {
      |    "stop here"
      |  }
      |}
      """.stripMargin.trim()
    )
    addBreakpoint("Sample.scala", 2)
    runDebugger("Sample") {
      waitForBreakpoint()
      evalEquals("(1, 2, 3)", "(1,2,3)")
    }
  }

  def testSmartBoxing() {
    addFileToProject("Sample.scala",
      """
      |object Sample {
      |  def foo[T](x: T)(y: T) = x
      |  def main(args: Array[String]) {
      |    val tup = (1, 2)
      |    "stop here"
      |  }
      |  def test(tup: (Int,  Int)) = tup._1
      |  def test2(tup: Tuple2[Int,  Int]) = tup._2
      |}
      """.stripMargin.trim()
    )
    addBreakpoint("Sample.scala", 4)
    runDebugger("Sample") {
      waitForBreakpoint()
      evalEquals("test(tup)", "1")
      evalEquals("test((1, 2))", "1")
      evalEquals("test(Tuple2(1, 2))", "1")
      evalEquals("test2(tup)", "2")
      evalEquals("test2((1, 2))", "2")
      evalEquals("test2(Tuple2(1, 2))", "2")
      evalEquals("foo(1)(2)", "1")
      evalEquals("scala.collection.immutable.HashSet.empty + 1 + 2", "Set(1, 2)")
    }
  }
  
  def testAssignment() {
    addFileToProject("Sample.scala",
      """
      |object Sample {
      |  var m = 0
      |  def main(args: Array[String]) {
      |    var z = 1
      |    val x: Array[Array[Int]] = Array(Array(1, 2), Array(2, 3))
      |    "stop here"
      |  }
      |}
      """.stripMargin.trim()
    )
    addBreakpoint("Sample.scala", 5)
    runDebugger("Sample") {
      waitForBreakpoint()
      evalEquals("x(0)(0)", "1")
      evalEquals("x(0)(0) = 2", "2")
      evalEquals("x(0)(0)", "2")
      evalEquals("z", "1")
      evalEquals("z = 2", "2")
      evalEquals("z", "2")
      evalEquals("m", "0")
      evalEquals("m = 2", "undefined")
      evalEquals("m", "2")
    }
  }

  def testThis() {
    addFileToProject("Sample.scala",
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
    addFileToProject("Sample.scala",
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
    addFileToProject("Sample.scala",
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
      evalEquals("1 toString ()", "1")
    }
  }

  def testIfUnit() {
    addFileToProject("Sample.scala",
      """
      |object Sample {
      |  def main(args: Array[String]) {
      |    "stop here"
      |  }
      |}
      """.stripMargin.trim()
    )
    addBreakpoint("Sample.scala", 2)
    runDebugger("Sample") {
      waitForBreakpoint()
      evalEquals("if (true) \"text\"", "undefined")
    }
  }

  def testIf() {
    addFileToProject("Sample.scala",
      """
      |object Sample {
      |  def main(args: Array[String]) {
      |    "stop here"
      |  }
      |}
      """.stripMargin.trim()
    )
    addBreakpoint("Sample.scala", 2)
    runDebugger("Sample") {
      waitForBreakpoint()
      evalEquals("if (true) \"text\" else \"next\"", "text")
    }
  }

  def testIfElse() {
    addFileToProject("Sample.scala",
      """
      |object Sample {
      |  def main(args: Array[String]) {
      |    "stop here"
      |  }
      |}
      """.stripMargin.trim()
    )
    addBreakpoint("Sample.scala", 2)
    runDebugger("Sample") {
      waitForBreakpoint()
      evalEquals("if (false) \"text\" else \"next\"", "next")
    }
  }

  def testNotNull() {
    addFileToProject("Sample.scala",
      """
      |object Sample {
      |  def main(args: Array[String]) {
      |    "stop here"
      |  }
      |}
      """.stripMargin.trim()
    )
    addBreakpoint("Sample.scala", 2)
    runDebugger("Sample") {
      waitForBreakpoint()
      evalEquals("\"text\" != null", "true")
    }
  }

  def testBackticks() {
    addFileToProject("Sample.scala",
      """
        |object Sample {
        |  def main(args: Array[String]) {
        |    val `val` = 100
        |    "stop here"
        |  }
        |}
      """.stripMargin.trim()
    )
    addBreakpoint("Sample.scala", 3)
    runDebugger("Sample") {
      waitForBreakpoint()
      evalEquals("`val`", "100")
    }
  }

}