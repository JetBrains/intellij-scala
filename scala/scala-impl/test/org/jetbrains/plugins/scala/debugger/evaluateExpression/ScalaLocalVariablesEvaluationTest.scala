package org.jetbrains.plugins.scala
package debugger
package evaluateExpression

import org.junit.experimental.categories.Category

@Category(Array(classOf[FlakyTests])) // works locally, may fail on server
class ScalaLocalVariablesEvaluationTest_2_11 extends ScalaLocalVariablesEvaluationTestBase {
  override protected def supportedIn(version: ScalaVersion) = version  <= LatestScalaVersions.Scala_2_11
}

@Category(Array(classOf[DebuggerTests]))
class ScalaLocalVariablesEvaluationTest_2_12 extends ScalaLocalVariablesEvaluationTestBase {
  override protected def supportedIn(version: ScalaVersion) =
    version >= LatestScalaVersions.Scala_2_12 && version <= LatestScalaVersions.Scala_2_13
}

@Category(Array(classOf[DebuggerTests]))
class ScalaLocalVariablesEvaluationTest_3_0 extends ScalaLocalVariablesEvaluationTestBase {
  override protected def supportedIn(version: ScalaVersion) = version  >= LatestScalaVersions.Scala_3_0

  override def testLocalObjectOuter(): Unit = failing(super.testLocalObjectOuter())

  override def testLocalMethodLocalObject(): Unit = failing(super.testLocalMethodLocalObject())
}

@Category(Array(classOf[DebuggerTests]))
abstract class ScalaLocalVariablesEvaluationTestBase extends ScalaDebuggerTestCase {
  addFileWithBreakpoints("Local.scala",
    s"""
       |object Local {
       |  def main(args: Array[String]): Unit = {
       |    val x = 1
       |    println()$bp
       |  }
       |}
      """.stripMargin.trim()
  )
  def testLocal(): Unit = {
    runDebugger() {
      waitForBreakpoint()
      evalEquals("x", "1")
    }
  }

  addFileWithBreakpoints("LocalClassParam.scala",
    s"""
       |class LocalClassParam(x: Int) {
       |  val h = x
       |  def foo(): Unit = {
       |    val y = () => {
       |      println()$bp
       |      1 + 2 + x
       |    }
       |    y()
       |  }
       |}
       |object LocalClassParam {
       |  def main(args: Array[String]): Unit = {
       |    val a = new LocalClassParam(1)
       |    a.foo()
       |  }
       |}
      """.stripMargin.trim()
  )
  def testLocalClassParam(): Unit = {
    runDebugger() {
      waitForBreakpoint()
      evalEquals("x", "1")
    }
  }

  addFileWithBreakpoints("ClassParamInConstructor.scala",
    s"""
       |class ClassParamInConstructor(
       |  unused: Int,
       |  used: Int,
       |  val field: Int
       |) {
       |  println(s"in constructor $$used")$bp
       |}
       |object ClassParamInConstructor {
       |  def main(args: Array[String]): Unit = {
       |    val a = new ClassParamInConstructor(1, 2, 3)
       |  }
       |}
       |""".stripMargin)
  def testClassParamInConstructor(): Unit = {
    runDebugger() {
      waitForBreakpoint()
      evalEquals("unused", "1")
      evalEquals("used", "2")
      evalEquals("field", "3")
    }
  }

  addFileWithBreakpoints("BackingFieldParamInMethod.scala",
    s"""
       |class BackingFieldParamInMethod(
       |    backingField: Int
       |) {
       |  def foo: Int = backingField$bp
       |}
       |object BackingFieldParamInMethod {
       |  def main(args: Array[String]): Unit = {
       |    val a = new BackingFieldParamInMethod(1)
       |    a.foo
       |  }
       |}
       |""".stripMargin)
  def testBackingFieldParamInMethod(): Unit = {
    runDebugger() {
      waitForBreakpoint()
      evalEquals("backingField", "1")
    }
  }

  addFileWithBreakpoints("BackingFieldParamInConstructor.scala",
    s"""
       |class BackingFieldParamInConstructor(
       |    backingField: Int
       |) {
       |  println("in constructor")$bp
       |
       |  def foo: Int = backingField
       |}
       |object BackingFieldParamInConstructor {
       |  def main(args: Array[String]): Unit = {
       |    val a = new BackingFieldParamInConstructor(1)
       |    a.foo
       |  }
       |}
       |""".stripMargin)
  def testBackingFieldParamInConstructor(): Unit = {
    runDebugger() {
      waitForBreakpoint()
      evalEquals("backingField", "1")
    }
  }

  addFileWithBreakpoints("NoBackingFieldParam.scala",
    s"""
       |class NoBackingFieldParam(
       |    noBackingField: Int
       |) {
       |  def foo: Int = 5$bp
       |}
       |object NoBackingFieldParam {
       |  def main(args: Array[String]): Unit = {
       |    val a = new NoBackingFieldParam(1)
       |    a.foo
       |  }
       |}
       |""".stripMargin)
  def testNoBackingFieldParam(): Unit = {
    runDebugger() {
      waitForBreakpoint()
      evalStartsWith(s"noBackingField", "constructor parameter 'noBackingField' is inaccessible outside of the class constructor")
    }
  }

  addFileWithBreakpoints("LocalFromForStatement.scala",
    s"""
       |object LocalFromForStatement {
       |  def main(args: Array[String]): Unit = {
       |    val x = 1
       |    for (i <- 1 to 1) {
       |      x
       |      println()$bp
       |    }
       |  }
       |}
      """.stripMargin.trim()
  )
  def testLocalFromForStatement(): Unit = {
    runDebugger() {
      waitForBreakpoint()
      evalEquals("x", "1")
    }
  }

  addFileWithBreakpoints("LocalFromForStmtFromOut.scala",
    s"""
       |object LocalFromForStmtFromOut {
       |  def main(args: Array[String]): Unit = {
       |    val x = 1
       |    for (i <- 1 to 1) {
       |      x
       |      println()$bp
       |    }
       |  }
       |}
      """.stripMargin.trim()
  )
  def testLocalFromForStmtFromOut(): Unit = {
    runDebugger() {
      waitForBreakpoint()
      evalEquals("x", "1")
    }
  }

  addFileWithBreakpoints("Param.scala",
    s"""
       |object Param {
       |  def foo(x: Int): Unit = {
       |    println()$bp
       |  }
       |
       |  def main(args: Array[String]): Unit = {
       |    val x = 0
       |    foo(x + 1)
       |  }
       |}
      """.stripMargin.trim()
  )
  def testParam(): Unit = {
    runDebugger() {
      waitForBreakpoint()
      evalEquals("x", "1")
    }
  }

  addFileWithBreakpoints("LocalParam.scala",
    s"""
       |object LocalParam {
       |  def main(args: Array[String]): Unit = {
       |    def foo(x: Int): Unit = {
       |      println()$bp
       |    }
       |    foo(1)
       |  }
       |}
      """.stripMargin.trim()
  )
  def testLocalParam(): Unit = {
    runDebugger() {
      waitForBreakpoint()
      evalEquals("x", "1")
    }
  }

  addFileWithBreakpoints("LocalOuter.scala",
    s"""
       |object LocalOuter {
       |  def main(args: Array[String]): Unit = {
       |    val x = 1
       |    val runnable = new Runnable {
       |      def run(): Unit = {
       |        x
       |        println()$bp
       |      }
       |    }
       |    runnable.run()
       |  }
       |}
      """.stripMargin.trim()
  )
  def testLocalOuter(): Unit = {
    runDebugger() {
      waitForBreakpoint()
      evalEquals("x", "1")
    }
  }

  addFileWithBreakpoints("LocalOuterOuter.scala",
    s"""
       |object LocalOuterOuter {
       |  def main(args: Array[String]): Unit = {
       |    val x = 1
       |    var y = "a"
       |    val runnable = new Runnable {
       |      def run(): Unit = {
       |        val runnable = new Runnable {
       |          def run(): Unit = {
       |            x
       |            println()$bp
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
  def testLocalOuterOuter(): Unit = {
    runDebugger() {
      waitForBreakpoint()
      evalEquals("x", "1")
      evalEquals("y", "a")
    }
  }

  addFileWithBreakpoints("LocalObjectOuter.scala",
    s"""
       |object LocalObjectOuter {
       |  def main(args: Array[String]): Unit = {
       |    object x {}
       |    val runnable = new Runnable {
       |      def run(): Unit = {
       |        val runnable = new Runnable {
       |          def run(): Unit = {
       |            x
       |            println()$bp
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
  def testLocalObjectOuter(): Unit = {
    runDebugger() {
      waitForBreakpoint()
      evalStartsWith("x", "LocalObjectOuter$x")
    }
  }

  addFileWithBreakpoints("LocalFromClosureAndClass.scala",
    s"""
       |object LocalFromClosureAndClass {
       |  def main(args: Array[String]): Unit = {
       |    val x = 1
       |    var y = "a"
       |    val runnable = new Runnable {
       |      def run(): Unit = {
       |        val foo = () => {
       |          val runnable = new Runnable {
       |            def run(): Unit = {
       |              x
       |              println()$bp
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
  def testLocalFromClosureAndClass(): Unit = {
    runDebugger() {
      waitForBreakpoint()
      evalEquals("x", "1")
      evalEquals("y", "a")
    }
  }

  addFileWithBreakpoints("LocalMethodLocal.scala",
    s"""
       |object LocalMethodLocal {
       |  def main(args: Array[String]): Unit = {
       |    val x: Int = 1
       |    var s = "a"
       |    def foo(y: Int): Unit = {
       |      println()$bp
       |      x
       |    }
       |    foo(2)
       |  }
       |}
      """.stripMargin.trim()
  )
  def testLocalMethodLocal(): Unit = {
    runDebugger() {
      waitForBreakpoint()
      evalEquals("x", "1")
      evalEquals("s", "a")
    }
  }

  addFileWithBreakpoints("LocalMethodLocalObject.scala",
    s"""
       |object LocalMethodLocalObject {
       |  def main(args: Array[String]): Unit = {
       |    object x
       |    def foo(y: Int): Unit = {
       |      x
       |      println()$bp
       |    }
       |    foo(2)
       |  }
       |}
      """.stripMargin.trim()
  )
  def testLocalMethodLocalObject(): Unit = {
    runDebugger() {
      waitForBreakpoint()
      evalStartsWith("x", "LocalMethodLocalObject$x")
    }
  }

  addFileWithBreakpoints("LocalMethodLocalMethodLocal.scala",
    s"""
       |object LocalMethodLocalMethodLocal {
       |  def main(args: Array[String]): Unit = {
       |    val x = 1
       |    var s = "a"
       |    def foo(y: Int): Unit = {
       |      def foo(y: Int): Unit = {
       |        println()$bp
       |         x
       |      }
       |      foo(y)
       |    }
       |    foo(2)
       |  }
       |}
      """.stripMargin.trim()
  )
  def testLocalMethodLocalMethodLocal(): Unit = {
    runDebugger() {
      waitForBreakpoint()
      evalEquals("x", "1")
      evalEquals("s", "a")
    }
  }

  addFileWithBreakpoints("LocalMethodLocalMethodLocalClass.scala",
    s"""
       |object LocalMethodLocalMethodLocalClass {
       |  def main(args: Array[String]): Unit = {
       |    val x = 1
       |    var s = "a"
       |    def foo(y: Int): Unit = {
       |      def foo(y: Int): Unit = {
       |        class A {
       |          def foo(): Unit = {
       |           println()$bp
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
  def testLocalMethodLocalMethodLocalClass(): Unit = {
    runDebugger() {
      waitForBreakpoint()
      evalEquals("x", "1")
      evalEquals("s", "a")
    }
  }

  addFileWithBreakpoints("LocMethLocMethLocClassLocMeth.scala",
    s"""
       |object LocMethLocMethLocClassLocMeth {
       |  def main(args: Array[String]): Unit = {
       |    val x = 1
       |    def foo(y: Int): Unit = {
       |      def foo(y: Int): Unit = {
       |        class A {
       |          def foo(): Unit = {
       |            class B {
       |              def foo(): Unit = {
       |                def goo(y: Int): Unit = {
       |                  println()$bp
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
  def testLocMethLocMethLocClassLocMeth(): Unit = {
    runDebugger() {
      waitForBreakpoint()
      evalEquals("x", "1")
    }
  }

  addFileWithBreakpoints("LocalObjectInside.scala",
    s"""
       |object LocalObjectInside {
       |  def main(args: Array[String]): Unit = {
       |    val x = 1
       |    object X {
       |      def foo(y: Int): Unit = {
       |        println()$bp
       |         x
       |      }
       |    }
       |    X.foo(2)
       |  }
       |}
      """.stripMargin.trim()
  )
  def testLocalObjectInside(): Unit = {
    runDebugger() {
      waitForBreakpoint()
      evalEquals("x", "1")
    }
  }

  addFileWithBreakpoints("LocalObjectInsideClassLevel.scala",
    s"""
       |object LocalObjectInsideClassLevel {
       |  def main(args: Array[String]): Unit = {
       |    class Local {
       |      def foo(): Unit = {
       |        val x = 1
       |        var s = "a"
       |        object X {
       |          def foo(y: Int): Unit = {
       |            println()$bp
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
  def testLocalObjectInsideClassLevel(): Unit = {
    runDebugger() {
      waitForBreakpoint()
      evalEquals("x", "1")
      evalEquals("s", "a")
    }
  }

  addFileWithBreakpoints("LocalUseNamedArgs.scala",
    s"""
       |object LocalUseNamedArgs {
       |  def main(args: Array[String]): Unit = {
       |    val j = 2
       |
       |    def inner(z: Int = 1) = bar(i = z, j = j)
       |    def inner2() = bar(i = 1, j = j )
       |
       |    println()$bp
       |  }
       |
       |  def bar(i: Int, j: Int): Int = i + j
       |}
      """.stripMargin.trim
  )
  def testLocalUseNamedArgs(): Unit = {
    runDebugger() {
      waitForBreakpoint()
      evalEquals("inner()", "3")
      evalEquals("inner(2)", "4")
      evalEquals("inner2()", "3")
      evalEquals("inner2", "3")
    }
  }
}