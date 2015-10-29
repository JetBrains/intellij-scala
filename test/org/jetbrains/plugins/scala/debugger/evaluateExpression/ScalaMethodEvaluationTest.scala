package org.jetbrains.plugins.scala.debugger.evaluateExpression

import org.jetbrains.plugins.scala.debugger.{ScalaDebuggerTestCase, ScalaVersion_2_11, ScalaVersion_2_12_M2}


/**
 * User: Alefas
 * Date: 17.10.11
 */

class ScalaMethodEvaluationTest extends ScalaMethodEvaluationTestBase with ScalaVersion_2_11

class ScalaMethodEvaluationTest_2_12_M2 extends ScalaMethodEvaluationTestBase with ScalaVersion_2_12_M2

abstract class ScalaMethodEvaluationTestBase extends ScalaDebuggerTestCase {
  def testBigIntAndSorted() {
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
      evalStartsWith("BigInt(2)", "2")
      evalEquals("Seq(4, 3, 2, 1).sorted", "List(1, 2, 3, 4)")
    }
  }

  def testBoxing() {
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
      evalStartsWith("1 -> 2", "(1,2)")
      evalEquals("123.toString", "123")
    }
  }
  
  def testSmartBoxing() {
    addFileToProject("Sample.scala",
      """
      |object Sample {
      |  def foo(x: AnyVal) = 1
      |  def goo(x: Int) = x + 1
      |  def main(args: Array[String]) {
      |    val z = java.lang.Integer.valueOf(5)
      |    "stop here"
      |  }
      |}
      """.stripMargin.trim()
    )
    addBreakpoint("Sample.scala", 5)
    runDebugger("Sample") {
      waitForBreakpoint()
      evalEquals("foo(1)", "1")
      evalEquals("goo(z)", "6")
    }
  }  

  def testChangingFunction() {
    addFileToProject("Sample.scala",
      """
      |object Sample {
      |  var i = 1
      |  def foo = {
      |    i = i + 1
      |    i
      |  }
      |  def main(args: Array[String]) {
      |    "stop here"
      |  }
      |}
      """.stripMargin.trim()
    )
    addBreakpoint("Sample.scala", 7)
    runDebugger("Sample") {
      waitForBreakpoint()
      evalEquals("foo", "2")
      evalEquals("foo", "3")
    }
  }

  def testSimpleFunction() {
    addFileToProject("Sample.scala",
      """
      |object Sample {
      |  def foo() = 2
      |  def main(args: Array[String]) {
      |    "stop here"
      |  }
      |}
      """.stripMargin.trim()
    )
    addBreakpoint("Sample.scala", 3)
    runDebugger("Sample") {
      waitForBreakpoint()
      evalEquals("foo", "2")
    }
  }

  def testPrivateMethods() {
    addFileToProject("Sample.scala",
      """
        |import Sample._
        |object Sample {
        |  private def foo() = 2
        |  def main(args: Array[String]) {
        |    "stop here"
        |  }
        |}
        |class Sample {
        |  private def bar() = 1
        |}
      """.stripMargin.trim()
    )
    addBreakpoint("Sample.scala", 4)
    runDebugger("Sample") {
      waitForBreakpoint()
      evalEquals("foo", "2")
      evalEquals("new Sample().bar()", "1")
    }
  }
  
  def testApplyCall() {
    addFileToProject("Sample.scala",
      """
      |object Sample {
      |  class A {
      |    def apply(x: Int) = x + 1
      |  }
      |  def main(args : Array[String]) {
      |    val a = new A()
      |    "stop here"
      |  }
      |}
      """.stripMargin.trim()
    )
    addBreakpoint("Sample.scala", 6)
    runDebugger("Sample") {
      waitForBreakpoint()
      evalEquals("a(-1)", "0")
      evalEquals("Array(\"a\", \"b\")", "[a,b]")
    }
  }

  def testAppliesFromScalaLibrary(): Unit = {
    addFileToProject("Sample.scala",
      """object Sample {
        |  def main(args : Array[String]) {
        |    "stop here"
        |  }
        |}
      """.stripMargin.trim()
    )
    addBreakpoint("Sample.scala", 2)
    runDebugger("Sample") {
      waitForBreakpoint()
      evalEquals("List[Int](1, 2)", "List(1, 2)")
      evalEquals("List(1, 2)", "List(1, 2)")
      evalEquals("Some(\"a\")", "Some(a)")
      evalEquals("Option(\"a\")", "Some(a)")
    }
  }
  
  def testCurriedFunction() {
    addFileToProject("Sample.scala",
      """
      |object Sample {
      |  def foo(x: Int)(y: Int) = x * 2 + y
      |  def main(args: Array[String]) {
      |    "stop here"
      |  }
      |}
      """.stripMargin.trim()
    )
    addBreakpoint("Sample.scala", 3)
    runDebugger("Sample") {
      waitForBreakpoint()
      evalEquals("foo(1)(2)", "4")
    }
  }
  
  def testArrayApplyFunction() {
    addFileToProject("Sample.scala",
      """
      |object Sample {
      |  def main(args : Array[String]) {
      |    val s = Array.ofDim[String](2, 2)
      |    s(1)(1) = "test"
      |    "stop here"
      |  }
      |}
      """.stripMargin.trim()
    )
    addBreakpoint("Sample.scala", 4)
    runDebugger("Sample") {
      waitForBreakpoint()
      evalEquals("s(1)(1)", "test")
    }
  }

  def testOverloadedFunction() {
    addFileToProject("Sample.scala",
      """
      |object Sample {
      |  def foo(x: Int) = 1
      |  def foo(x: String) = 2
      |  def main(args: Array[String]) {
      |    "stop here"
      |  }
      |}
      """.stripMargin.trim()
    )
    addBreakpoint("Sample.scala", 4)
    runDebugger("Sample") {
      waitForBreakpoint()
      evalEquals("foo(1)", "1")
      evalEquals("foo(\"\")", "2")
    }
  }

  def testImplicitConversion() {
    addFileToProject("Sample.scala",
      """
      |object Sample {
      |  def main(args : Array[String]) {
      |    "stop here"
      |  }
      |}
      """.stripMargin.trim()
    )
    addBreakpoint("Sample.scala", 2)
    runDebugger("Sample") {
      waitForBreakpoint()
      evalEquals("\"test\".dropRight(2)", "te")
      evalEquals("\"3\" -> \"3\"", "(3,3)")
      evalEquals("(1 - 3).abs", "2")
    }
  }

  def testSequenceArgument() {
    addFileToProject("Sample.scala",
      """
      |object Sample {
      |  def moo(x: String*) = x.foldLeft(0)(_ + _.length())
      |  def main(args: Array[String]) {
      |    val x = Seq("a", "b")
      |    "stop here"
      |  }
      |}
      """.stripMargin.trim()
    )
    addBreakpoint("Sample.scala", 4)
    runDebugger("Sample") {
      waitForBreakpoint()
      evalEquals("moo(x: _*)", "2")
    }
  }

  def testArrayLengthFunction() {
    addFileToProject("Sample.scala",
      """
      |object Sample {
      |  def main(args : Array[String]) {
      |    val s = Array(1, 2, 3)
      |    "stop here"
      |  }
      |}
      """.stripMargin.trim()
    )
    addBreakpoint("Sample.scala", 3)
    runDebugger("Sample") {
      waitForBreakpoint()
      evalEquals("s.length", "3")
      evalEquals("s.length()", "3")
    }
  }

  def testSimpleFunctionFromInner() {
    addFileToProject("Sample.scala",
      """
      |object Sample {
      |  def foo() = 2
      |  def main(args: Array[String]) {
      |    val x = 1
      |    val r = () => {
      |      x
      |      "stop here"
      |    }
      |    r()
      |  }
      |}
      """.stripMargin.trim()
    )
    addBreakpoint("Sample.scala", 6)
    runDebugger("Sample") {
      waitForBreakpoint()
      evalEquals("foo", "2")
    }
  }

  def testLibraryFunction() {
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
      evalStartsWith("scala.collection.mutable.ArrayBuffer.newBuilder", "ArrayBuffer()")
    }
  }
  
  def testDynamicFunctionApplication() {
    addFileToProject("Sample.scala",
      """
      |class A
      |class B extends A {
      |  def foo() = 1
      |}
      |object Sample {
      |  def main(args: Array[String]) {
      |    val a: A = new B
      |    "stop here"
      |  }
      |}
      """.stripMargin.trim()
    )
    addBreakpoint("Sample.scala", 7)
    runDebugger("Sample") {
      waitForBreakpoint()
      evalEquals("a.foo()", "1")
    }
  }

  def testSubstringFunction() {
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
      evalStartsWith("\"test\".substring(0, 2)", "te")
      evalStartsWith("\"test\".substring(2)", "st")
    }
  }

  def testNonStaticFunction() {
    addFileToProject("Sample.scala",
      """
      |object Sample {
      |  def foo() = 2
      |  val x = 1
      |  def main(args: Array[String]) {
      |    def moo() {}
      |    class A {
      |      val x = 1
      |      def goo() = 2
      |      def foo() {
      |        val r = () => {
      |          moo()
      |          x
      |          "stop here"
      |        }
      |        r()
      |      }
      |    }
      |
      |    new A().foo()
      |  }
      |}
      """.stripMargin.trim()
    )
    addBreakpoint("Sample.scala", 12)
    runDebugger("Sample") {
      waitForBreakpoint()
      evalStartsWith("goo", "2")
    }
  }

  def testDefaultAndNamedParameters() {
    addFileToProject("Sample.scala",
      """
      |object Sample {
      |  def foo(x: Int, y: Int = 1, z: Int)(h: Int = x + y, m: Int) = x + y + z + h + m
      |  def main(args: Array[String]) {
      |    "stop here"
      |  }
      |}
      """.stripMargin.trim()
    )
    addBreakpoint("Sample.scala", 3)
    runDebugger("Sample") {
      waitForBreakpoint()
      evalEquals("foo(1, z = 1)(m = 1)", "6")
      evalEquals("foo(1, 2, 1)(m = 1)", "8")
      evalEquals("foo(1, 2, 1)(1, m = 1)", "6")
    }
  }

  def testRepeatedParameters() {
    addFileToProject("Sample.scala",
      """
      |object Sample {
      |  def foo(x: String*) = x.foldLeft("")(_ + _)
      |  def main(args: Array[String]) {
      |    "stop here"
      |  }
      |}
      """.stripMargin.trim()
    )
    addBreakpoint("Sample.scala", 3)
    runDebugger("Sample") {
      waitForBreakpoint()
      evalEquals("foo(\"a\", \"b\", \"c\")", "abc")
      evalEquals("foo(\"a\")", "a")
      evalEquals("foo()", "")
      evalEquals("Array[Byte](0, 1)", "[0,1]")
    }
  }

  def testImplicitParameters() {
    addFileToProject("Sample.scala",
      """
      |object Sample {
      |  def moo(x: Int)(implicit s: String) = x + s.length()
      |  def foo(x: Int)(implicit y: Int) = {
      |    implicit val s = "test"
      |    "stop here"
      |    x + y
      |  }
      |  def main(args: Array[String]) {
      |    implicit val x = 1
      |    foo(1)
      |  }
      |}
      """.stripMargin.trim()
    )
    addBreakpoint("Sample.scala", 4)
    runDebugger("Sample") {
      waitForBreakpoint()
      evalEquals("foo(1)", "2")
      evalEquals("foo(1)(2)", "3")
      evalEquals("moo(1)", "5")
      evalEquals("moo(1)(\"a\")", "2")
    }
  }

  def testCaseClasses() {
    addFileToProject("com/Sample.scala",
      """
      |package com
      |case class A(x: Int)
      |object Sample {
      |  case class B(x: Int)
      |  def main(args: Array[String]) {
      |    "stop here"
      |  }
      |}
      """.stripMargin.trim()
    )
    addBreakpoint("com/Sample.scala", 5)
    runDebugger("com.Sample") {
      waitForBreakpoint()
      evalEquals("A(1)", "A(1)")
      evalEquals("A.apply(1)", "A(1)")
      evalEquals("B(1)", "B(1)")
      evalEquals("B.apply(1)", "B(1)")
    }
  }

  def testPrivateInTrait(): Unit = {
    addFileToProject("com/Sample.scala",
      """package com
        |trait PrivateTrait {
        |
        |  private[this] def privThis(i: Int) = i + 1
        |
        |  private def priv(i: Int) = i + 2
        |
        |  private val privConst = 42
        |
        |  def open() = {
        |    ""
        |  }
        |}
        |
        |object Sample {
        |  class A extends PrivateTrait
        |
        |  def main(args: Array[String]) {
        |    val a = new A
        |    a.open()
        |  }
        |}""".stripMargin)
    addBreakpoint("com/Sample.scala", 10)
    runDebugger("com.Sample") {
      waitForBreakpoint()
      evalEquals("priv(0)", "2")
      evalEquals("privThis(0)", "1")
      evalEquals("privConst", "42")
    }
  }

  def testLocalsInTrait(): Unit = {
    addFileToProject("Sample.scala",
    """trait TTT {
      |  def foo() = {
      |    def bar() = {
      |      def baz() = 1
      |      baz() //stop here
      |    }
      |    bar()
      |  }
      |}
      |
      |object Sample {
      |  class A extends TTT
      |
      |  def main(args: Array[String]) {
      |    val a = new A
      |    a.foo()
      |  }
      |}
    """.stripMargin)
    addBreakpoint("Sample.scala", 4)
    runDebugger("Sample") {
      waitForBreakpoint()
      evalEquals("bar()", "1")
      evalEquals("bar", "1")
      evalEquals("baz()", "1")
      evalEquals("foo()", "1")
      evalEquals("foo + bar", "2")
    }
  }
}