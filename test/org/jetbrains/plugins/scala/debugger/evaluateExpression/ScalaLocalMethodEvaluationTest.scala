package org.jetbrains.plugins.scala.debugger.evaluateExpression

/**
 * User: Alefas
 * Date: 15.10.11
 */

class ScalaLocalMethodEvaluationTest extends ScalaDebuggerTestCase {
  def testSimpleLocalFunction() {
    myFixture.addFileToProject("Sample.scala",
      """
      |object Sample {
      |  def main(args: Array[String]) {
      |    def foo: Int = 1
      |    "stop here"
      |  }
      |}
      """.stripMargin.trim()
    )
    addBreakpoint("Sample.scala", 3)
    runDebugger("Sample") {
      waitForBreakpoint()
      evalEquals("foo", "1")
    }
  }

  def testLocalFunctionWithParameters() {
    myFixture.addFileToProject("Sample.scala",
      """
      |object Sample {
      |  def main(args: Array[String]) {
      |    val y = "test"
      |    def foo(x: Int): Int = x + y.length
      |    "stop here"
      |  }
      |}
      """.stripMargin.trim()
    )
    addBreakpoint("Sample.scala", 4)
    runDebugger("Sample") {
      waitForBreakpoint()
      evalEquals("foo(3)", "7")
    }
  }

  def testSimpleLocalFunctionWithParameters() {
    myFixture.addFileToProject("Sample.scala",
      """
      |object Sample {
      |  def main(args: Array[String]) {
      |    val x = 2
      |    def foo: Int = x - 1
      |    "stop here"
      |  }
      |}
      """.stripMargin.trim()
    )
    addBreakpoint("Sample.scala", 4)
    runDebugger("Sample") {
      waitForBreakpoint()
      evalEquals("foo", "1")
    }
  }

  def testSimpleLocalFunctionWithParametersWithDifferentParameters1() {
    myFixture.addFileToProject("Sample.scala",
      """
      |object Sample {
      |  def main(args: Array[String]) {
      |    val x = 2
      |    val y = "c"
      |    def foo: Int = x - y.length()
      |    "stop here"
      |  }
      |}
      """.stripMargin.trim()
    )
    addBreakpoint("Sample.scala", 5)
    runDebugger("Sample") {
      waitForBreakpoint()
      evalEquals("foo", "1")
    }
  }

  def testSimpleLocalFunctionWithParametersWithDifferentParameters2() {
    myFixture.addFileToProject("Sample.scala",
      """
      |object Sample {
      |  def main(args: Array[String]) {
      |    val y = "c"
      |    val x = 2
      |    def foo: Int = x - y.length()
      |    "stop here"
      |  }
      |}
      """.stripMargin.trim()
    )
    addBreakpoint("Sample.scala", 5)
    runDebugger("Sample") {
      waitForBreakpoint()
      evalEquals("foo", "1")
    }
  }

  def testSimpleLocalFunctionWithParametersWithDifferentParameters3() {
    myFixture.addFileToProject("Sample.scala",
      """
      |object Sample {
      |  def main(args: Array[String]) {
      |    val y = "c"
      |    val x = 2
      |    def foo: Int = - y.length() + x
      |    "stop here"
      |  }
      |}
      """.stripMargin.trim()
    )
    addBreakpoint("Sample.scala", 5)
    runDebugger("Sample") {
      waitForBreakpoint()
      evalEquals("foo", "1")
    }
  }

  def testLocalFunctionWithLocalObject() {
    myFixture.addFileToProject("Sample.scala",
      """
      |object Sample {
      |  def main(args: Array[String]) {
      |    object y {val y = 1}
      |    val x = 2
      |    def foo: Int = x - y.y
      |    "stop here"
      |  }
      |}
      """.stripMargin.trim()
    )
    addBreakpoint("Sample.scala", 5)
    runDebugger("Sample") {
      waitForBreakpoint()
      evalEquals("foo", "1")
    }
  }

  def testLocalFunctionWithField() {
    myFixture.addFileToProject("Sample.scala",
      """
      |object Sample {
      |  def main(args: Array[String]) {
      |    val g = 1
      |    def moo(x: Int) = g + x
      |    val zz = (y: Int) => {
      |      val uu = (x: Int) => {
      |        g
      |        "stop here"
      |      }
      |      uu(1)
      |    }
      |    zz(2)
      |  }
      |}
      """.stripMargin.trim()
    )
    addBreakpoint("Sample.scala", 7)
    runDebugger("Sample") {
      waitForBreakpoint()
      evalEquals("moo(x)", "2")
    }
  }

  def testLocalFromAnonymous() {
    myFixture.addFileToProject("Sample.scala",
      """
      |object Sample {
      |  val y = 1
      |  def main(args: Array[String]) {
      |    val x = 2
      |    def foo: Int = x - y
      |    "stop here"
      |  }
      |}
      """.stripMargin.trim()
    )
    addBreakpoint("Sample.scala", 5)
    runDebugger("Sample") {
      waitForBreakpoint()
      evalEquals("foo", "1")
    }
  }

  def testClojure() {
    myFixture.addFileToProject("Sample.scala",
      """
        |object Sample {
        |  def main(args: Array[String]) {
        |    def outer() {
        |      val s = "start"
        |      def inner(a: String, b: String): String = {
        |        "stop here"
        |        s + a + b
        |      }
        |      inner("aa", "bb")
        |    }
        |    outer()
        |  }
        |}
      """.stripMargin.trim()
    )
    addBreakpoint("Sample.scala", 6)
    runDebugger("Sample") {
      waitForBreakpoint()
      evalEquals("a", "aa")
      evalEquals("b", "bb")
      evalEquals("s", "start")
      evalEquals("inner(\"qq\", \"ww\")", "startqqww")
    }
  }

    //this test should work, but it doesn't (last two assertions)
//  def testClojureWithDefaultParameter() {
//    myFixture.addFileToProject("Sample.scala",
//      """
//        |object Sample {
//        |  def main(args: Array[String]) {
//        |    def outer() {
//        |      val s = "start"
//        |      val d = "default"
//        |      def inner(a: String, b: String = d): String = {
//        |        "stop here"
//        |        s + a + b
//        |      }
//        |      inner("aa")
//        |    }
//        |    outer()
//        |  }
//        |}
//      """.stripMargin.trim()
//    )
//    addBreakpoint("Sample.scala", 6)
//    runDebugger("Sample") {
//      waitForBreakpoint()
//      evalEquals("a", "aa")
//      evalEquals("b", "default")
//      evalEquals("s", "start")
//      evalEquals("inner(\"aa\", \"bb\")", "startaabb")
//      evalEquals("inner(\"aa\")", "startaadefault")
//    }
//  }
}