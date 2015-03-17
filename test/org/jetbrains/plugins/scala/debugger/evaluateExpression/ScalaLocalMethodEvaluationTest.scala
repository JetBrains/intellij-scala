package org.jetbrains.plugins.scala.debugger.evaluateExpression

import org.jetbrains.plugins.scala.debugger.ScalaDebuggerTestCase

/**
 * User: Alefas
 * Date: 15.10.11
 */

class ScalaLocalMethodEvaluationTest extends ScalaDebuggerTestCase {
  def testSimple() {
    addFileToProject("Sample.scala",
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

  def testLocalWithParameters() {
    addFileToProject("Sample.scala",
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

  def testSimpleLocalWithParams() {
    addFileToProject("Sample.scala",
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

  def testSimpleLocalWithDiffParams1() {
    addFileToProject("Sample.scala",
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

  def testSimpleLocalWithDiffParams2() {
    addFileToProject("Sample.scala",
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

  def testSimpleLocalWithDiffParams3() {
    addFileToProject("Sample.scala",
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

  def testLocalWithLocalObject() {
    addFileToProject("Sample.scala",
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

  def testLocalWithField() {
    addFileToProject("Sample.scala",
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
    addFileToProject("Sample.scala",
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
    addFileToProject("Sample.scala",
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

  def testLocalWithDefaultAndNamedParams() {
    addFileToProject("Sample.scala",
      """
        |object Sample {
        |  def main(args: Array[String]) {
        |    def outer() {
        |      def inner(a: String, b: String = "default", c: String = "other"): String = {
        |        "stop here"
        |        a + b + c
        |      }
        |      inner("aa")
        |    }
        |    outer()
        |  }
        |}
      """.stripMargin.trim()
    )
    addBreakpoint("Sample.scala", 5)
    runDebugger("Sample") {
      waitForBreakpoint()
      evalEquals("a", "aa")
      evalEquals("b", "default")
      evalEquals("c", "other")
      evalEquals("inner(\"aa\", \"bb\")", "aabbother")
      evalEquals("inner(\"aa\")", "aadefaultother")
      evalEquals("inner(\"aa\", c = \"cc\")", "aadefaultcc")
    }
  }
//
//  def testLocalMethodsWithSameName() {
//    addFileToProject("Sample.scala",
//      """
//        |object Sample {
//        |  def main(args: Array[String]) {
//        |    def foo(i: Int = 1) = {
//        |      def foo(j: Int = 2) = j
//        |      i
//        |    }
//        |    "stop"
//        |    def other() {
//        |      def foo(i: Int = 3) = i
//        |      "stop"
//        |    }
//        |    def third() {
//        |      def foo(i: Int = 4) = i
//        |      "stop"
//        |    }
//        |    foo()
//        |    other()
//        |    third()
//        |  }
//        |}
//      """.stripMargin.trim())
//    addBreakpoint("Sample.scala", 4)
//    addBreakpoint("Sample.scala", 6)
//    addBreakpoint("Sample.scala", 9)
//    addBreakpoint("Sample.scala", 13)
//    runDebugger("Sample") {
//      //todo test for multiple breakpoints?
//      waitForBreakpoint()
//      evalEquals("foo()", "1")
//      waitForBreakpoint()
//      evalEquals("foo()", "2")
//
//
//    }
//  }

  //todo this test should work, but it doesn't (last two assertions)
  def testClojureWithDefaultParameter() {
    addFileToProject("Sample.scala",
      """
        |object Sample {
        |  def main(args: Array[String]) {
        |    def outer() {
        |      val s = "start"
        |      val d = "default"
        |      def inner(a: String, b: String = d): String = {
        |        "stop here"
        |        s + a + b
        |      }
        |      inner("aa")
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
      evalEquals("b", "default")
      evalEquals("s", "start")
      evalEquals("inner(\"aa\", \"bb\")", "startaabb")
      evalEquals("inner(\"aa\")", "startaadefault")
    }
  }

  def testFunctionsWithLocalParameters(): Unit = {
    addFileToProject("Sample.scala",
      """
        |object Sample {
        |  def main(args: Array[String]) {
        |    val x = 1
        |    val y = 2
        |    def outer() = {
        |      val s = "start"
        |      val d = "default"
        |      def inner(a: String, b: String = d): String = {
        |        val z = s + a + b + y
        |        def inInner() = {
        |          z + x
        |        }
        |        inInner()
        |        "stop here"
        |        z
        |      }
        |      inner("aa")
        |    }
        |    outer()
        |  }
        |}
      """.stripMargin.trim()
    )
    addBreakpoint("Sample.scala", 13)
    runDebugger("Sample") {
      waitForBreakpoint()
      evalEquals("a", "aa")
      evalEquals("b", "default")
      evalEquals("x", "1")
      evalEquals("y", "2")
      evalEquals("s", "start")
      evalEquals("z", "startaadefault2")
      evalEquals("inInner()", "startaadefault21")
      evalEquals("inner(\"aa\", \"bb\")", "startaabb2")
      evalEquals("inner(\"aa\")", "startaadefault2")
      evalEquals("outer()", "startaadefault2")
    }
  }
}