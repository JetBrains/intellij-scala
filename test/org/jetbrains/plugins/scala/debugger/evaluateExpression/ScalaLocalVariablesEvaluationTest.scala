package org.jetbrains.plugins.scala.debugger.evaluateExpression

/**
 * User: Alefas
 * Date: 13.10.11
 */
class ScalaLocalVariablesEvaluationTest extends ScalaDebuggerTestCase {
  def testLocal() {
    myFixture.addFileToProject("Sample.scala",
      """
      |object Sample {
      |  def main(args: Array[String]) {
      |    val x = 1
      |    "stop here"
      |  }
      |}
      """.stripMargin.trim()
    )
    addBreakpoint("Sample.scala", 3)
    runDebugger("Sample") {
      waitForBreakpoint()
      evalEquals("x", "1")
    }
  }
  
  def testLocalClassParameter() {
    myFixture.addFileToProject("Sample.scala",
      """
      |class A(x: Int) {
      |  val h = x
      |  def foo() {
      |    val y = () => {
      |      "stop here"
      |      1 + 2 + x
      |    }
      |    y()
      |  }
      |}
      |object Sample {
      |  def main(args: Array[String]) {
      |    val a = new A(1)
      |    a.foo()
      |  }
      |}
      """.stripMargin.trim()
    )
    addBreakpoint("Sample.scala", 5)
    runDebugger("Sample") {
      waitForBreakpoint()
      evalEquals("x", "1")
    }
  }

  def testLocalFromForStatement() {
    myFixture.addFileToProject("Sample.scala",
      """
      |object Sample {
      |  def main(args: Array[String]) {
      |    val x = 1
      |    for (i <- 1 to 1) {
      |      x
      |      "stop here"
      |    }
      |  }
      |}
      """.stripMargin.trim()
    )
    addBreakpoint("Sample.scala", 5)
    runDebugger("Sample") {
      waitForBreakpoint()
      evalEquals("x", "1")
    }
  }

  def testLocalFromForStatementFromOutside() {
    myFixture.addFileToProject("Sample.scala",
      """
      |object Sample {
      |  def main(args: Array[String]) {
      |    val x = 1
      |    for (i <- 1 to 1) {
      |      x
      |      "stop here"
      |    }
      |  }
      |}
      """.stripMargin.trim()
    )
    addBreakpoint("Sample.scala", 3)
    runDebugger("Sample") {
      waitForBreakpoint()
      evalEquals("x", "1")
    }
  }

  def testParam() {
    myFixture.addFileToProject("Sample.scala",
      """
      |object Sample {
      |  def foo(x: Int) {
      |    "stop here"
      |  }
      |
      |  def main(args: Array[String]) {
      |    val x = 0
      |    foo(x + 1)
      |  }
      |}
      """.stripMargin.trim()
    )
    addBreakpoint("Sample.scala", 2)
    runDebugger("Sample") {
      waitForBreakpoint()
      evalEquals("x", "1")
    }
  }

  def testLocalParam() {
    myFixture.addFileToProject("Sample.scala",
      """
      |object Sample {
      |  def main(args: Array[String]) {
      |    def foo(x: Int) {
      |      "stop here"
      |    }
      |    foo(1)
      |  }
      |}
      """.stripMargin.trim()
    )
    addBreakpoint("Sample.scala", 3)
    runDebugger("Sample") {
      waitForBreakpoint()
      evalEquals("x", "1")
    }
  }
  
  def testLocalOuter() {
    myFixture.addFileToProject("Sample.scala",
      """
      |object Sample {
      |  def main(args: Array[String]) {
      |    val x = 1
      |    val runnable = new Runnable {
      |      def run() {
      |        x
      |        "stop here"
      |      }
      |    }
      |    runnable.run()
      |  }
      |}
      """.stripMargin.trim()
    )
    addBreakpoint("Sample.scala", 6)
    runDebugger("Sample") {
      waitForBreakpoint()
      evalEquals("x", "1")
    }
  }

  def testLocalOuterOuter() {
    myFixture.addFileToProject("Sample.scala",
      """
        |object Sample {
        |  def main(args: Array[String]) {
        |    val x = 1
        |    var y = "a"
        |    val runnable = new Runnable {
        |      def run() {
        |        val runnable = new Runnable {
        |          def run() {
        |            x
        |            "stop here"
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
    addBreakpoint("Sample.scala", 9)
    runDebugger("Sample") {
      waitForBreakpoint()
      evalEquals("x", "1")
      evalEquals("y", "a")
    }
  }

  def testLocalObjectOuter() {
    myFixture.addFileToProject("Sample.scala",
      """
      |object Sample {
      |  def main(args: Array[String]) {
      |    object x {}
      |    val runnable = new Runnable {
      |      def run() {
      |        val runnable = new Runnable {
      |          def run() {
      |            x
      |            "stop here"
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
    addBreakpoint("Sample.scala", 8)
    runDebugger("Sample") {
      waitForBreakpoint()
      evalStartsWith("x", "Sample$x")
    }
  }
  
  def testLocalOuterFromClojureAndClass() {
    myFixture.addFileToProject("Sample.scala",
      """
        |object Sample {
        |  def main(args: Array[String]) {
        |    val x = 1
        |    var y = "a"
        |    val runnable = new Runnable {
        |      def run() {
        |        val foo = () => {
        |          val runnable = new Runnable {
        |            def run() {
        |              x
        |              "stop here"
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
    addBreakpoint("Sample.scala", 9)
    runDebugger("Sample") {
      waitForBreakpoint()
      evalEquals("x", "1")
      evalEquals("y", "a")
    }
  }

  def testLocalMethodLocal() {
    myFixture.addFileToProject("Sample.scala",
      """
        |object Sample {
        |  def main(args: Array[String]) {
        |    val x: Int = 1
        |    var s = "a"
        |    def foo(y: Int) {
        |      "stop here"
        |      x
        |    }
        |    foo(2)
        |  }
        |}
      """.stripMargin.trim()
    )
    addBreakpoint("Sample.scala", 5)
    runDebugger("Sample") {
      waitForBreakpoint()
      evalEquals("x", "1")
      evalEquals("s", "a")
    }
  }

  def testLocalMethodLocalObject() {
    myFixture.addFileToProject("Sample.scala",
      """
      |object Sample {
      |  def main(args: Array[String]) {
      |    object x
      |    def foo(y: Int) {
      |      x
      |      "stop here"
      |    }
      |    foo(2)
      |  }
      |}
      """.stripMargin.trim()
    )
    addBreakpoint("Sample.scala", 5)
    runDebugger("Sample") {
      waitForBreakpoint()
      evalStartsWith("x", "Sample$x")
    }
  }

  def testLocalMethodLocalMethodLocal() {
    myFixture.addFileToProject("Sample.scala",
      """
        |object Sample {
        |  def main(args: Array[String]) {
        |    val x = 1
        |    var s = "a"
        |    def foo(y: Int) {
        |      def foo(y: Int) {
        |        "stop here"
        |         x
        |      }
        |      foo(y)
        |    }
        |    foo(2)
        |  }
        |}
      """.stripMargin.trim()
    )
    addBreakpoint("Sample.scala", 6)
    runDebugger("Sample") {
      waitForBreakpoint()
      evalEquals("x", "1")
      evalEquals("s", "a")
    }
  }

  def testLocalMethodLocalMethodLocalClass() {
    myFixture.addFileToProject("Sample.scala",
      """
        |object Sample {
        |  def main(args: Array[String]) {
        |    val x = 1
        |    var s = "a"
        |    def foo(y: Int) {
        |      def foo(y: Int) {
        |        class A {
        |          def foo() {
        |           "stop here"
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
    addBreakpoint("Sample.scala", 8)
    runDebugger("Sample") {
      waitForBreakpoint()
      evalEquals("x", "1")
      evalEquals("s", "a")
    }
  }
  
  def testLocalMethodLocalMethodLocalClassLocalMethod() {
    myFixture.addFileToProject("Sample.scala",
      """
      |object Sample {
      |  def main(args: Array[String]) {
      |    val x = 1
      |    def foo(y: Int) {
      |      def foo(y: Int) {
      |        class A {
      |          def foo() {
      |            class B {
      |              def foo() {
      |                def goo(y: Int) {
      |                  "stop here"
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
    addBreakpoint("Sample.scala", 10)
    runDebugger("Sample") {
      waitForBreakpoint()
      evalEquals("x", "1")
    }
  }
  
  def testLocalObjectInside() {
    myFixture.addFileToProject("Sample.scala",
      """
      |object Sample {
      |  def main(args: Array[String]) {
      |    val x = 1
      |    object X {
      |      def foo(y: Int) {
      |        "stop here"
      |         x
      |      }
      |    }
      |    X.foo(2)
      |  }
      |}
      """.stripMargin.trim()
    )
    addBreakpoint("Sample.scala", 5)
    runDebugger("Sample") {
      waitForBreakpoint()
      evalEquals("x", "1")
    }
  }

  def testLocalObjectInsideClassLevel() {
    myFixture.addFileToProject("Sample.scala",
      """
        |object Sample {
        |  def main(args: Array[String]) {
        |    class Local {
        |      def foo() {
        |        val x = 1
        |        var s = "a"
        |        object X {
        |          def foo(y: Int) {
        |            "stop here"
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
    addBreakpoint("Sample.scala", 8)
    runDebugger("Sample") {
      waitForBreakpoint()
      evalEquals("x", "1")
      evalEquals("s", "a")
    }
  }
}