package org.jetbrains.plugins.scala.debugger.evaluateExpression

import org.jetbrains.plugins.scala.debugger.{ScalaDebuggerTestCase, ScalaVersion_2_11, ScalaVersion_2_12}

/**
 * User: Alefas
 * Date: 19.10.11
 */

class ScalaExpressionsEvaluator extends ScalaExpressionsEvaluatorBase with ScalaVersion_2_11
class ScalaExpressionsEvaluator_212 extends ScalaExpressionsEvaluatorBase with ScalaVersion_2_12

abstract class ScalaExpressionsEvaluatorBase extends ScalaDebuggerTestCase {
  addFileWithBreakpoints("PrefixUnary.scala",
    s"""
      |object PrefixUnary {
      |  class U {
      |    def unary_!(): Boolean = false
      |  }
      |  def main(args: Array[String]) {
      |    val u = new U
      |    ""$bp
      |  }
      |}
    """.stripMargin.trim()
  )
  def testPrefixUnary() {
    runDebugger() {
      waitForBreakpoint()
      evalEquals("!u", "false")
      evalEquals("!true", "false")
    }
  }

  addFileWithBreakpoints("VariousExprs.scala",
    s"""
      |object VariousExprs {
      |  def main(args: Array[String]) {
      |    ""$bp
      |  }
      |}
    """.stripMargin.trim()
  )
  def testVariousExprs() {
    runDebugger() {
      waitForBreakpoint()
      evalEquals("(1, 2, 3)", "(1,2,3)")
      evalEquals("if (true) \"text\"", "undefined")
      evalEquals("if (true) \"text\" else \"next\"", "text")
      evalEquals("if (false) \"text\" else \"next\"", "next")
      evalEquals("\"text\" != null", "true")
    }
  }

  addFileWithBreakpoints("SmartBoxing.scala",
    s"""
      |object SmartBoxing {
      |  def foo[T](x: T)(y: T) = x
      |  def main(args: Array[String]) {
      |    val tup = (1, 2)
      |    ""$bp
      |  }
      |  def test(tup: (Int,  Int)) = tup._1
      |  def test2(tup: Tuple2[Int,  Int]) = tup._2
      |}
    """.stripMargin.trim()
  )
  def testSmartBoxing() {
    runDebugger() {
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

  addFileWithBreakpoints("Assignment.scala",
    s"""
      |object Assignment {
      |  var m = 0
      |  def main(args: Array[String]) {
      |    var z = 1
      |    val y = 0
      |    val x: Array[Array[Int]] = Array(Array(1, 2), Array(2, 3))
      |    ""$bp
      |  }
      |}
    """.stripMargin.trim()
  )
  def testAssignment() {
    runDebugger() {
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
      evalEquals("y = 1", "1") //local vals may be reassigned in debugger
      evalEquals("y", "1")
    }
  }

  addFileWithBreakpoints("This.scala",
    s"""
      |object This {
      |  def main(args: Array[String]) {
      |    class This {
      |      val x = 1
      |      def foo() {
      |       ""$bp
      |      }
      |    }
      |    new This().foo()
      |  }
      |}
    """.stripMargin.trim()
  )
  def testThis() {
    runDebugger() {
      waitForBreakpoint()
      evalEquals("this.x", "1")
    }
  }

  addFileWithBreakpoints("PrefixedThis.scala",
    s"""
      |object PrefixedThis {
      |  def main(args: Array[String]) {
      |    class This {
      |      val x = 1
      |      def foo() {
      |        val runnable = new Runnable {
      |          def run() {
      |            val x = () => {
      |             This.this.x //to have This.this in scope
      |             ""$bp
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
  def testPrefixedThis() {
    runDebugger() {
      waitForBreakpoint()
      evalEquals("This.this.x", "1")
    }
  }

  addFileWithBreakpoints("Postfix.scala",
    s"""
      |object Postfix {
      |  def main(args: Array[String]) {
      |    object x {val x = 1}
      |    x
      |    ""$bp
      |  }
      |}
    """.stripMargin.trim()
  )
  def testPostfix() {
    runDebugger() {
      waitForBreakpoint()
      evalEquals("x x", "1")
      evalEquals("1 toString ()", "1")
    }
  }

  addFileWithBreakpoints("Backticks.scala",
    s"""
      |object Backticks {
      |  def main(args: Array[String]) {
      |    val `val` = 100
      |    ""$bp
      |  }
      |}
    """.stripMargin.trim()
  )
  def testBackticks() {
    runDebugger() {
      waitForBreakpoint()
      evalEquals("`val`", "100")
    }
  }

  addFileWithBreakpoints("Literal.scala",
    s"""
      |object Literal {
      |  implicit def intToString(x: Int) = x.toString + x.toString
      |  def main(args: Array[String]) {
      |    val n = 1
      |    ""$bp
      |  }
      |}
    """.stripMargin.trim()
  )
  def testLiteral() {
    runDebugger() {
      waitForBreakpoint()
      evalEquals("\"x\".length", "1")
      evalEquals("s\"n = $n\"", "n = 1")
      evalEquals("1L", "1")
      evalEquals("'c'", "c")
      evalEquals("true", "true")
      evalEquals("null", "null")
      evalEquals("1", "1")
      evalEquals("1F", "1.0")
      evalEquals("Array(1F, 2.0F)", "[1.0,2.0]")
      evalEquals("123.charAt(3)", "1")
      evalEquals("\"a\".concat(123)", "a123123")
      evalEquals("'aaa", "'aaa")
      evalEquals("'aaa.name", "aaa")
    }
  }

  addFileWithBreakpoints("JavaLib.scala",
    s"""
      |object JavaLib {
      |  def main(args: Array[String]) {
      |    ""$bp
      |  }
      |}
    """.stripMargin.trim()
  )
  def testJavaLib() {
    runDebugger() {
      waitForBreakpoint()
      evalEquals("new StringBuilder(\"test\").append(23)", "test23")
      evalEquals("new Array[Int](2)", "[0,0]")
    }
  }

  addFileWithBreakpoints("InnerClass.scala",
    s"""
      |object InnerClass {
      |  class Expr {}
      |  def main(args: Array[String]) {
      |    ""$bp
      |  }
      |}
    """.stripMargin.trim()
  )
  def testInnerClass() {
    runDebugger() {
      waitForBreakpoint()
      evalStartsWith("new Expr", "InnerClass$Expr")
    }
  }

  addFileWithBreakpoints("OverloadingClass.scala",
    s"""
      |object OverloadingClass {
      |  class Expr(s: String) {
      |    def this(t: Int) {
      |      this("test")
      |    }
      |  }
      |  def main(args: Array[String]) {
      |    ""$bp
      |  }
      |}
    """.stripMargin.trim()
  )
  def testOverloadingClass() {
    runDebugger() {
      waitForBreakpoint()
      evalStartsWith("new Expr(\"\")", "OverloadingClass$Expr")
      evalStartsWith("new Expr(2)", "OverloadingClass$Expr")
    }
  }

  addFileWithBreakpoints("IsInstanceOf.scala",
    s"""
       |object IsInstanceOf {
       |  class A
       |  class B
       |  def main(args: Array[String]) {
       |    val x = new A
       |    val y = new B
       |    ""$bp
       |  }
       |}
      """.stripMargin.trim()
  )
  def testIsInstanceOf() {
    runDebugger() {
      waitForBreakpoint()
      evalEquals("x.isInstanceOf[A]", "true")
      evalEquals("x.isInstanceOf[B]", "false")
      evalEquals("y.isInstanceOf[B]", "true")
      evalEquals("y.isInstanceOf[String]", "false")
      evalEquals("\"\".isInstanceOf[String]", "true")
    }
  }

  addFileWithBreakpoints("SyntheticOperators.scala",
    s"""
       |object SyntheticOperators {
       |  def fail: Boolean = throw new Exception("fail!")
       |  def main(args: Array[String]) {
       |     val tr = true
       |     val fls = false
       |    ""$bp
       |  }
       |}
      """.stripMargin.trim()
  )
  def testSyntheticOperators(): Unit = {
    runDebugger() {
      waitForBreakpoint()
      evalEquals("tr || fail", "true")
      evalEquals("fls && fail", "false")
      evalEquals("fls || tr", "true")
      evalEquals("tr && fls", "false")
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
    }
  }

}