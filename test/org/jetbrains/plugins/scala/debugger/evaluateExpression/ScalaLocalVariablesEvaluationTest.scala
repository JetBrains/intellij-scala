package org.jetbrains.plugins.scala.debugger.evaluateExpression

import org.jetbrains.plugins.scala.debugger.{ScalaDebuggerTestCase, ScalaVersion_2_11, ScalaVersion_2_12}

/**
 * User: Alefas
 * Date: 13.10.11
 */
class ScalaLocalVariablesEvaluationTest extends ScalaLocalVariablesEvaluationTestBase with ScalaVersion_2_11
class ScalaLocalVariablesEvaluationTest_212 extends ScalaLocalVariablesEvaluationTestBase with ScalaVersion_2_12

abstract class ScalaLocalVariablesEvaluationTestBase extends ScalaDebuggerTestCase {
  addFileWithBreakpoints("Local.scala",
    s"""
       |object Local {
       |  def main(args: Array[String]) {
       |    val x = 1
       |    ""$bp
       |  }
       |}
      """.stripMargin.trim()
  )
  def testLocal() {
    runDebugger() {
      waitForBreakpoint()
      evalEquals("x", "1")
    }
  }

  addFileWithBreakpoints("LocalClassParam.scala",
    s"""
       |class LocalClassParam(x: Int) {
       |  val h = x
       |  def foo() {
       |    val y = () => {
       |      ""$bp
       |      1 + 2 + x
       |    }
       |    y()
       |  }
       |}
       |object LocalClassParam {
       |  def main(args: Array[String]) {
       |    val a = new LocalClassParam(1)
       |    a.foo()
       |  }
       |}
      """.stripMargin.trim()
  )
  def testLocalClassParam() {
    runDebugger() {
      waitForBreakpoint()
      evalEquals("x", "1")
    }
  }

  addFileWithBreakpoints("LocalFromForStatement.scala",
    s"""
       |object LocalFromForStatement {
       |  def main(args: Array[String]) {
       |    val x = 1
       |    for (i <- 1 to 1) {
       |      x
       |      ""$bp
       |    }
       |  }
       |}
      """.stripMargin.trim()
  )
  def testLocalFromForStatement() {
    runDebugger() {
      waitForBreakpoint()
      evalEquals("x", "1")
    }
  }

  addFileWithBreakpoints("LocalFromForStmtFromOut.scala",
    s"""
       |object LocalFromForStmtFromOut {
       |  def main(args: Array[String]) {
       |    val x = 1
       |    for (i <- 1 to 1) {
       |      x
       |      ""$bp
       |    }
       |  }
       |}
      """.stripMargin.trim()
  )
  def testLocalFromForStmtFromOut() {
    runDebugger() {
      waitForBreakpoint()
      evalEquals("x", "1")
    }
  }

  addFileWithBreakpoints("Param.scala",
    s"""
       |object Param {
       |  def foo(x: Int) {
       |    ""$bp
       |  }
       |
       |  def main(args: Array[String]) {
       |    val x = 0
       |    foo(x + 1)
       |  }
       |}
      """.stripMargin.trim()
  )
  def testParam() {
    runDebugger() {
      waitForBreakpoint()
      evalEquals("x", "1")
    }
  }

  addFileWithBreakpoints("LocalParam.scala",
    s"""
       |object LocalParam {
       |  def main(args: Array[String]) {
       |    def foo(x: Int) {
       |      ""$bp
       |    }
       |    foo(1)
       |  }
       |}
      """.stripMargin.trim()
  )
  def testLocalParam() {
    runDebugger() {
      waitForBreakpoint()
      evalEquals("x", "1")
    }
  }

  addFileWithBreakpoints("LocalOuter.scala",
    s"""
       |object LocalOuter {
       |  def main(args: Array[String]) {
       |    val x = 1
       |    val runnable = new Runnable {
       |      def run() {
       |        x
       |        ""$bp
       |      }
       |    }
       |    runnable.run()
       |  }
       |}
      """.stripMargin.trim()
  )
  def testLocalOuter() {
    runDebugger() {
      waitForBreakpoint()
      evalEquals("x", "1")
    }
  }

  addFileWithBreakpoints("LocalOuterOuter.scala",
    s"""
       |object LocalOuterOuter {
       |  def main(args: Array[String]) {
       |    val x = 1
       |    var y = "a"
       |    val runnable = new Runnable {
       |      def run() {
       |        val runnable = new Runnable {
       |          def run() {
       |            x
       |            ""$bp
       |          }
       |        }
       |        runnable.run()
       |      }
       |    }
       |    runnable.run()
       |  }
       |}
      """.stripMargin.trim()
  )
  def testLocalOuterOuter() {
    runDebugger() {
      waitForBreakpoint()
      evalEquals("x", "1")
      evalEquals("y", "a")
    }
  }

  addFileWithBreakpoints("LocalObjectOuter.scala",
    s"""
       |object LocalObjectOuter {
       |  def main(args: Array[String]) {
       |    object x {}
       |    val runnable = new Runnable {
       |      def run() {
       |        val runnable = new Runnable {
       |          def run() {
       |            x
       |            ""$bp
       |          }
       |        }
       |        runnable.run()
       |      }
       |    }
       |    runnable.run()
       |  }
       |}
      """.stripMargin.trim()
  )
  def testLocalObjectOuter() {
    runDebugger() {
      waitForBreakpoint()
      evalStartsWith("x", "LocalObjectOuter$x")
    }
  }

  addFileWithBreakpoints("LocalFromClosureAndClass.scala",
    s"""
       |object LocalFromClosureAndClass {
       |  def main(args: Array[String]) {
       |    val x = 1
       |    var y = "a"
       |    val runnable = new Runnable {
       |      def run() {
       |        val foo = () => {
       |          val runnable = new Runnable {
       |            def run() {
       |              x
       |              ""$bp
       |            }
       |          }
       |          runnable.run()
       |        }
       |        foo()
       |      }
       |    }
       |    runnable.run()
       |  }
       |}
      """.stripMargin.trim()
  )
  def testLocalFromClosureAndClass() {
    runDebugger() {
      waitForBreakpoint()
      evalEquals("x", "1")
      evalEquals("y", "a")
    }
  }

  addFileWithBreakpoints("LocalMethodLocal.scala",
    s"""
       |object LocalMethodLocal {
       |  def main(args: Array[String]) {
       |    val x: Int = 1
       |    var s = "a"
       |    def foo(y: Int) {
       |      ""$bp
       |      x
       |    }
       |    foo(2)
       |  }
       |}
      """.stripMargin.trim()
  )
  def testLocalMethodLocal() {
    runDebugger() {
      waitForBreakpoint()
      evalEquals("x", "1")
      evalEquals("s", "a")
    }
  }

  addFileWithBreakpoints("LocalMethodLocalObject.scala",
    s"""
       |object LocalMethodLocalObject {
       |  def main(args: Array[String]) {
       |    object x
       |    def foo(y: Int) {
       |      x
       |      ""$bp
       |    }
       |    foo(2)
       |  }
       |}
      """.stripMargin.trim()
  )
  def testLocalMethodLocalObject() {
    runDebugger() {
      waitForBreakpoint()
      evalStartsWith("x", "LocalMethodLocalObject$x")
    }
  }

  addFileWithBreakpoints("LocalMethodLocalMethodLocal.scala",
    s"""
       |object LocalMethodLocalMethodLocal {
       |  def main(args: Array[String]) {
       |    val x = 1
       |    var s = "a"
       |    def foo(y: Int) {
       |      def foo(y: Int) {
       |        ""$bp
       |         x
       |      }
       |      foo(y)
       |    }
       |    foo(2)
       |  }
       |}
      """.stripMargin.trim()
  )
  def testLocalMethodLocalMethodLocal() {
    runDebugger() {
      waitForBreakpoint()
      evalEquals("x", "1")
      evalEquals("s", "a")
    }
  }

  addFileWithBreakpoints("LocalMethodLocalMethodLocalClass.scala",
    s"""
       |object LocalMethodLocalMethodLocalClass {
       |  def main(args: Array[String]) {
       |    val x = 1
       |    var s = "a"
       |    def foo(y: Int) {
       |      def foo(y: Int) {
       |        class A {
       |          def foo() {
       |           ""$bp
       |            s + x
       |          }
       |        }
       |        new A().foo()
       |      }
       |      foo(y)
       |    }
       |    foo(2)
       |  }
       |}
      """.stripMargin.trim()
  )
  def testLocalMethodLocalMethodLocalClass() {
    runDebugger() {
      waitForBreakpoint()
      evalEquals("x", "1")
      evalEquals("s", "a")
    }
  }

  addFileWithBreakpoints("LocMethLocMethLocClassLocMeth.scala",
    s"""
       |object LocMethLocMethLocClassLocMeth {
       |  def main(args: Array[String]) {
       |    val x = 1
       |    def foo(y: Int) {
       |      def foo(y: Int) {
       |        class A {
       |          def foo() {
       |            class B {
       |              def foo() {
       |                def goo(y: Int) {
       |                  ""$bp
       |                  x
       |                }
       |                goo(x + 1)
       |              }
       |            }
       |            new B().foo()
       |          }
       |        }
       |        new A().foo()
       |      }
       |      foo(y)
       |    }
       |    foo(2)
       |  }
       |}
      """.stripMargin.trim()
  )
  def testLocMethLocMethLocClassLocMeth() {
    runDebugger() {
      waitForBreakpoint()
      evalEquals("x", "1")
    }
  }

  addFileWithBreakpoints("LocalObjectInside.scala",
    s"""
       |object LocalObjectInside {
       |  def main(args: Array[String]) {
       |    val x = 1
       |    object X {
       |      def foo(y: Int) {
       |        ""$bp
       |         x
       |      }
       |    }
       |    X.foo(2)
       |  }
       |}
      """.stripMargin.trim()
  )
  def testLocalObjectInside() {
    runDebugger() {
      waitForBreakpoint()
      evalEquals("x", "1")
    }
  }

  addFileWithBreakpoints("LocalObjectInsideClassLevel.scala",
    s"""
       |object LocalObjectInsideClassLevel {
       |  def main(args: Array[String]) {
       |    class Local {
       |      def foo() {
       |        val x = 1
       |        var s = "a"
       |        object X {
       |          def foo(y: Int) {
       |            ""$bp
       |             x
       |          }
       |        }
       |        X.foo(2)
       |      }
       |    }
       |    new Local().foo()
       |  }
       |}
      """.stripMargin.trim()
  )
  def testLocalObjectInsideClassLevel() {
    runDebugger() {
      waitForBreakpoint()
      evalEquals("x", "1")
      evalEquals("s", "a")
    }
  }
}