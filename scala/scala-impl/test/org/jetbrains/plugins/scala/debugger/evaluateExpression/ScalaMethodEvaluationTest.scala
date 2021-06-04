package org.jetbrains.plugins.scala
package debugger
package evaluateExpression

import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

// !!! NOTE: some of these methods are failing: SCL-16528
// TODO: cleanup running against different versions
//  separate supported in and actual running versions
//  looks like "supported in" should generally use the lowers minor version (language level in other words)
//  and "run with" can run with various minor versions
@RunWith(classOf[JUnit4])
@Category(Array(classOf[DebuggerTests]))
class ScalaMethodEvaluationTest_2_11 extends ScalaMethodEvaluationTestBase {
  override protected def supportedIn(version: ScalaVersion) = version == LatestScalaVersions.Scala_2_11

  //todo move to ScalaMethodEvaluationTestBase when SCL-17927 is fixed
  @Test
  override def testInForStmt(): Unit = {
    runDebugger() {
      waitForBreakpoint()
      evalEquals("getX", "1")
      evalEquals("getX1", "2")
      evalEquals("getY()", "3")
      evalEquals("getY1", "4")
      evalEquals("new Inner().foo", "1")
      atNextBreakpoint {
        evalEquals("getX", "1")
        evalEquals("getX1", "2")
        evalEquals("getY()", "3")
        evalEquals("getY1", "4")
        evalEquals("new Inner().foo", "1")
      }
    }
  }
}

@RunWith(classOf[JUnit4])
@Category(Array(classOf[DebuggerTests]))
class ScalaMethodEvaluationTest_2_12 extends ScalaMethodEvaluationTestBase {
  override protected def supportedIn(version: ScalaVersion) = version == LatestScalaVersions.Scala_2_12
}

@RunWith(classOf[JUnit4])
@Category(Array(classOf[DebuggerTests]))
class ScalaMethodEvaluationTest_2_13 extends ScalaMethodEvaluationTestBase {
  override protected def supportedIn(version: ScalaVersion) = version == LatestScalaVersions.Scala_2_13
}

@RunWith(classOf[JUnit4])
@Category(Array(classOf[DebuggerTests]))
class ScalaMethodEvaluationTest_3_0 extends ScalaMethodEvaluationTestBase {
  override protected def supportedIn(version: ScalaVersion) = version >= LatestScalaVersions.Scala_3_0

  //todo fix
  override def testNonStaticFunction(): Unit            = failing(super.testNonStaticFunction())
  override def testLocalFunctions(): Unit               = failing(super.testLocalFunctions())
  override def testClosure(): Unit                      = failing(super.testClosure())
  override def testClosureWithDefaultParameter(): Unit  = failing(super.testClosureWithDefaultParameter())
  override def testFunctionsWithLocalParameters(): Unit = failing(super.testFunctionsWithLocalParameters())
  override def testDefaultArgsInTrait(): Unit           = failing(super.testDefaultArgsInTrait())
  override def testLocalMethodsWithSameName(): Unit     = failing(super.testLocalMethodsWithSameName())
  override def testLocalMethodsWithSameName1(): Unit    = failing(super.testLocalMethodsWithSameName1())
  override def testLocalMethodsWithSameName2(): Unit    = failing(super.testLocalMethodsWithSameName2())

  addSourceFile("one.scala", "def one() = 1")
  addSourceFile("a/two.scala",
    """package a
      |
      |def two() = 2
      |""".stripMargin)
  addSourceFile("a/b/three.scala",
    """package a.b
      |
      |def three =
      |  3
      |""".stripMargin)
  addFileWithBreakpoints("topLevel.scala",
    s"""import a.two
       |import a.b.three
       |
       |@main
       |def topLevel(): Unit =
       |  def local() = "local"
       |  println()$bp
       |
       |private val ten = 10
       |private def fortyTwo(): Int = 42
       |""".stripMargin)
  @Test
  def testtopLevel(): Unit =
    runDebugger() {
      waitForBreakpoint()
      evalEquals("one()", "1")
      evalEquals("two()", "2")
      evalEquals("three", "3")
      evalEquals("ten", "10")
      evalEquals("fortyTwo()", "42")
      evalEquals("local()", "local")
    }
}

@RunWith(classOf[JUnit4])
@Category(Array(classOf[DebuggerTests]))
abstract class ScalaMethodEvaluationTestBase extends ScalaDebuggerTestCase {

  addFileWithBreakpoints("SmartBoxing.scala",
   s"""
      |object SmartBoxing {
      |  def foo(x: AnyVal) = 1
      |  def goo(x: Int) = x + 1
      |  def main(args: Array[String]): Unit = {
      |    val z = java.lang.Integer.valueOf(5)
      |    println()$bp
      |  }
      |}
    """.stripMargin.trim()
  )

  @Test
  def testSmartBoxing(): Unit = {
    runDebugger() {
      waitForBreakpoint()
      evalEquals("foo(1)", "1")
      evalEquals("goo(z)", "6")
    }
  }

  addFileWithBreakpoints("FunctionWithSideEffects.scala",
   s"""
      |object FunctionWithSideEffects {
      |  var i = 1
      |  def foo = {
      |    i = i + 1
      |    i
      |  }
      |  def main(args: Array[String]): Unit = {
      |    println()$bp
      |  }
      |}
    """.stripMargin.trim()
  )
  @Test
  def testFunctionWithSideEffects(): Unit = {
    runDebugger() {
      waitForBreakpoint()
      evalEquals("foo", "2")
      evalEquals("foo", "3")
    }
  }

  addFileWithBreakpoints("SimpleFunction.scala",
   s"""
      |object SimpleFunction {
      |  def foo() = 2
      |  def main(args: Array[String]): Unit = {
      |    println()$bp
      |  }
      |}
    """.stripMargin.trim()
  )
  @Test
  def testSimpleFunction(): Unit = {
    runDebugger() {
      waitForBreakpoint()
      evalEquals("foo", "2")
    }
  }

  addFileWithBreakpoints("PrivateMethods.scala",
   s"""
      |import PrivateMethods._
      |object PrivateMethods {
      |  private def foo() = 2
      |  def main(args: Array[String]): Unit = {
      |    println()$bp
      |  }
      |}
      |class PrivateMethods {
      |  private def bar() = 1
      |}
    """.stripMargin.trim()
  )

  @Test
  def testPrivateMethods(): Unit = {
    runDebugger() {
      waitForBreakpoint()
      evalEquals("foo", "2")
      evalEquals("new PrivateMethods().bar()", "1")
    }
  }

  addFileWithBreakpoints("ApplyCall.scala",
   s"""
      |object ApplyCall {
      |  class A {
      |    def apply(x: Int) = x + 1
      |  }
      |  def main(args: Array[String]): Unit = {
      |    val a = new A()
      |    println()$bp
      |  }
      |}
    """.stripMargin.trim()
  )

  @Test
  def testApplyCall(): Unit = {
    runDebugger() {
      waitForBreakpoint()
      evalEquals("a(-1)", "0")
      evalEquals("Array(\"a\", \"b\")", "[a,b]")
    }
  }

  addFileWithBreakpoints("CurriedFunction.scala",
   s"""
      |object CurriedFunction {
      |  def foo(x: Int)(y: Int) = x * 2 + y
      |  def main(args: Array[String]): Unit = {
      |    println()$bp
      |  }
      |}
    """.stripMargin.trim()
  )

  @Test
  def testCurriedFunction(): Unit = {
    runDebugger() {
      waitForBreakpoint()
      evalEquals("foo(1)(2)", "4")
    }
  }

  addFileWithBreakpoints("ArrayApplyFunction.scala",
   s"""
      |object ArrayApplyFunction {
      |  def main(args: Array[String]): Unit = {
      |    val s = Array.ofDim[String](2, 2)
      |    s(1)(1) = "test"
      |    println()$bp
      |  }
      |}
    """.stripMargin.trim()
  )

  @Test
  def testArrayApplyFunction(): Unit = {
    runDebugger() {
      waitForBreakpoint()
      evalEquals("s(1)(1)", "test")
    }
  }

  addFileWithBreakpoints("OverloadedFunction.scala",
   s"""
      |object OverloadedFunction {
      |  def foo(x: Int) = 1
      |  def foo(x: String) = 2
      |  def main(args: Array[String]): Unit = {
      |    println()$bp
      |  }
      |}
    """.stripMargin.trim()
  )

  @Test
  def testOverloadedFunction(): Unit = {
    runDebugger() {
      waitForBreakpoint()
      evalEquals("foo(1)", "1")
      evalEquals("foo(\"\")", "2")
    }
  }

  addFileWithBreakpoints("ImplicitConversion.scala",
   s"""
      |object ImplicitConversion {
      |  def main(args: Array[String]): Unit = {
      |    println()$bp
      |  }
      |}
    """.stripMargin.trim()
  )

  @Test
  def testImplicitConversion(): Unit = {
    runDebugger() {
      waitForBreakpoint()
      evalEquals("\"test\".dropRight(2)", "te")
      evalEquals("\"3\" -> \"3\"", "(3,3)")
      evalEquals("(1 - 3).abs", "2")
    }
  }

  addFileWithBreakpoints("SequenceArgument.scala",
   s"""
      |object SequenceArgument {
      |  def moo(x: String*) = x.foldLeft(0)(_ + _.length())
      |  def main(args: Array[String]): Unit = {
      |    val x = Seq("a", "b")
      |    println()$bp
      |  }
      |}
    """.stripMargin.trim()
  )

  @Test
  def testSequenceArgument(): Unit = {
    runDebugger() {
      waitForBreakpoint()
      evalEquals("moo(x: _*)", "2")
    }
  }

  addFileWithBreakpoints("ArrayLengthFunction.scala",
   s"""
      |object ArrayLengthFunction {
      |  def main(args: Array[String]): Unit = {
      |    val s = Array(1, 2, 3)
      |    println()$bp
      |  }
      |}
    """.stripMargin.trim()
  )

  @Test
  def testArrayLengthFunction(): Unit = {
    runDebugger() {
      waitForBreakpoint()
      evalEquals("s.length", "3")
    }
  }

  addFileWithBreakpoints("SimpleFunctionFromInner.scala",
   s"""
      |object SimpleFunctionFromInner {
      |  def foo() = 2
      |  def main(args: Array[String]): Unit = {
      |    val x = 1
      |    val r = () => {
      |      x
      |      println()$bp
      |    }
      |    r()
      |  }
      |}
    """.stripMargin.trim()
  )

  @Test
  def testSimpleFunctionFromInner(): Unit = {
    runDebugger() {
      waitForBreakpoint()
      evalEquals("foo", "2")
    }
  }

  addFileWithBreakpoints("LibraryFunctions.scala",
   s"""
      |object LibraryFunctions {
      |  def main(args: Array[String]): Unit = {
      |    println()$bp
      |  }
      |}
    """.stripMargin.trim()
  )

  @Test
  def testLibraryFunctions(): Unit = {
    runDebugger() {
      waitForBreakpoint()
      evalStartsWith("scala.collection.mutable.ArrayBuffer.empty", "ArrayBuffer()")
      evalStartsWith("\"test\".substring(0, 2)", "te")
      evalStartsWith("\"test\".substring(2)", "st")
      evalEquals("List[Int](1, 2)", "List(1, 2)")
      evalEquals("List(1, 2)", "List(1, 2)")
      evalEquals("Some(\"a\")", "Some(a)")
      evalEquals("Option(\"a\")", "Some(a)")
      evalStartsWith("1 -> 2", "(1,2)")
      evalEquals("123.toString", "123")
      evalStartsWith("BigInt(2)", "2")
      evalEquals("Seq(4, 3, 2, 1).sorted", "List(1, 2, 3, 4)")
    }
  }

  addFileWithBreakpoints("DynamicFunctionApplication.scala",
    s"""
       |class A
       |class B extends A {
       |  def foo() = 1
       |  def bar(s: String) = s
       |}
       |object DynamicFunctionApplication {
       |  def main(args: Array[String]): Unit = {
       |    val a: A = new B
       |    println()$bp
       |  }
       |}
      """.stripMargin.trim()
  )

  @Test
  def testDynamicFunctionApplication(): Unit = {
    runDebugger() {
      waitForBreakpoint()
      evalEquals("a.foo()", "1")
      evalEquals("a.bar(\"hi\")", "hi")
    }
  }

  addFileWithBreakpoints("NonStaticFunction.scala",
   s"""
      |object NonStaticFunction {
      |  def foo() = 2
      |  val x = 1
      |  def main(args: Array[String]): Unit = {
      |    def moo(): Unit = {}
      |    class A {
      |      val x = 1
      |      def goo() = 2
      |      def foo(): Unit = {
      |        val r = () => {
      |          moo()
      |          x
      |          println()$bp
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

  @Test
  def testNonStaticFunction(): Unit = {
    runDebugger() {
      waitForBreakpoint()
      evalStartsWith("goo", "2")
    }
  }

  addFileWithBreakpoints("DefaultAndNamedParameters.scala",
   s"""
      |object DefaultAndNamedParameters {
      |  def foo(x: Int, y: Int = 1, z: Int)(h: Int = x + y, m: Int) = x + y + z + h + m
      |  def main(args: Array[String]): Unit = {
      |    println()$bp
      |  }
      |}
    """.stripMargin.trim()
  )

  @Test
  def testDefaultAndNamedParameters(): Unit = {
    runDebugger() {
      waitForBreakpoint()
      evalEquals("foo(1, z = 1)(m = 1)", "6")
      evalEquals("foo(1, 2, 1)(m = 1)", "8")
      evalEquals("foo(1, 2, 1)(1, m = 1)", "6")
    }
  }

  addFileWithBreakpoints("RepeatedParameters.scala",
   s"""
      |object RepeatedParameters {
      |  def foo(x: String*) = x.foldLeft("")(_ + _)
      |  def main(args: Array[String]): Unit = {
      |    println()$bp
      |  }
      |}
    """.stripMargin.trim()
  )

  @Test
  def testRepeatedParameters(): Unit = {
    runDebugger() {
      waitForBreakpoint()
      evalEquals("foo(\"a\", \"b\", \"c\")", "abc")
      evalEquals("foo(\"a\")", "a")
      evalEquals("foo()", "")
      evalEquals("Array[Byte](0, 1)", "[0,1]")
    }
  }

  addFileWithBreakpoints("ImplicitParameters.scala",
   s"""
      |object ImplicitParameters {
      |  def moo(x: Int)(implicit s: String) = x + s.length()
      |  def foo(x: Int)(implicit y: Int) = {
      |    implicit val s = "test"
      |    println()$bp
      |    x + y
      |  }
      |  def main(args: Array[String]): Unit = {
      |    implicit val x = 1
      |    foo(1)
      |  }
      |}
    """.stripMargin.trim()
  )

  @Test
  def testImplicitParameters(): Unit = {
    runDebugger() {
      waitForBreakpoint()
      evalEquals("foo(1)", "2")
      evalEquals("foo(1)(2)", "3")
      evalEquals("moo(1)", "5")
      evalEquals("moo(1)(\"a\")", "2")
    }
  }

  addFileWithBreakpoints("CaseClasses.scala",
   s"""
      |case class CCA(x: Int)
      |object CaseClasses {
      |  case class CCB(x: Int)
      |  def main(args: Array[String]): Unit = {
      |    println()$bp
      |  }
      |}
    """.stripMargin.trim()
  )

  @Test
  def testCaseClasses(): Unit = {
    runDebugger() {
      waitForBreakpoint()
      evalEquals("CCA(1)", "CCA(1)")
      evalEquals("CCA.apply(1)", "CCA(1)")
      evalEquals("CCB(1)", "CCB(1)")
      evalEquals("CCB.apply(1)", "CCB(1)")
    }
  }

  addFileWithBreakpoints("PrivateInTrait.scala",
   s"""
      |trait Privates {
      |
      |  private[this] def privThis(i: Int) = i + 1
      |
      |  private def priv(i: Int) = i + 2
      |
      |  private val privConst = 42
      |
      |  def open() = {
      |    println()$bp
      |  }
      |}
      |
      |object PrivateInTrait {
      |  class A extends Privates
      |
      |  def main(args: Array[String]): Unit = {
      |    val a = new A
      |    a.open()
      |  }
      |}""".stripMargin)

  @Test
  def testPrivateInTrait(): Unit = {
    runDebugger() {
      waitForBreakpoint()
      evalEquals("priv(0)", "2")
      evalEquals("privThis(0)", "1")
      evalEquals("privConst", "42")
    }
  }

  addFileWithBreakpoints("LocalsInTrait.scala",
   s"""trait TTT {
      |  def foo() = {
      |    def bar() = {
      |      def baz() = 1
      |      baz()$bp
      |    }
      |    bar()
      |  }
      |}
      |
      |object LocalsInTrait {
      |  class A extends TTT
      |
      |  def main(args: Array[String]): Unit = {
      |    val a = new A
      |    a.foo()
      |  }
      |}
    """.stripMargin)

  @Test
  def testLocalsInTrait(): Unit = {
    runDebugger() {
      waitForBreakpoint()
      evalEquals("bar()", "1")
      evalEquals("bar", "1")
      evalEquals("baz()", "1")
      evalEquals("foo()", "1")
      evalEquals("foo + bar", "2")
    }
  }

  // tests for local functions ----------------------------------------------

  addFileWithBreakpoints("LocalFunctions.scala",
    s"""
       |object LocalFunctions {
       |  val field = 1
       |
       |  def main(args: Array[String]): Unit = {
       |    simple()
       |    withParameters()
       |    withParamFromLocal()
       |    withDiffParams1()
       |    withDiffParams2()
       |    withDiffParams3()
       |    withObject()
       |    withAnonfunField()
       |    useField()
       |  }
       |
       |  def simple(): Unit = {
       |    def foo1: Int = 1
       |    println()$bp
       |  }
       |
       |  def withParameters(): Unit = {
       |    val y = "test"
       |    def foo2(x: Int): Int = x + y.length
       |    println()$bp
       |  }
       |
       |  def withParamFromLocal(): Unit = {
       |    val x = 2
       |    def foo3: Int = x - 1
       |    println()$bp
       |  }
       |
       |  def withDiffParams1(): Unit = {
       |    val x = 2
       |    val y = "c"
       |    def foo4: Int = x - y.length()
       |    println()$bp
       |  }
       |
       |  def withDiffParams2(): Unit = {
       |    val y = "c"
       |    val x = 2
       |    def foo5(): Int = x - y.length()
       |    println()$bp
       |  }
       |
       |  def withDiffParams3(): Unit = {
       |    val y = "c"
       |    val x = 2
       |    def foo6: Int = - y.length() + x
       |    println()$bp
       |  }
       |
       |  def withObject(): Unit = {
       |    object y {val y = 1}
       |    val x = 2
       |    def foo7: Int = x - y.y
       |    println()$bp
       |  }
       |
       |  def withAnonfunField(): Unit = {
       |    val g = 1
       |    def moo(x: Int) = g + x
       |    val zz = (y: Int) => {
       |      val uu = (x: Int) => {
       |        g
       |        println()$bp
       |      }
       |      uu(1)
       |    }
       |    zz(2)
       |  }
       |
       |  def useField(): Unit = {
       |    val x = 2
       |    def foo8: Int = x - field
       |    println()$bp
       |  }
       |}
    """.stripMargin.trim()
  )

  @Test
  @Category(Array(classOf[FlakyTests]))
  def testLocalFunctions(): Unit = {
    runDebugger() {
      waitForBreakpoint()
      evalEquals("foo1", "1")
      atNextBreakpoint {
        evalEquals("foo2(3)", "7")
      }
      atNextBreakpoint {
        evalEquals("foo3", "1")
      }
      atNextBreakpoint {
        evalEquals("foo4", "1")
      }
      atNextBreakpoint {
        evalEquals("foo5", "1")
      }
      atNextBreakpoint {
        evalEquals("foo6", "1")
      }
      atNextBreakpoint {
        evalEquals("foo7", "1")
      }
      atNextBreakpoint {
        evalEquals("moo(x)", "2")
      }
      atNextBreakpoint {
        evalEquals("foo8", "1")
      }
    }
  }

  addFileWithBreakpoints("Closure.scala",
    s"""
       |object Closure {
       |  def main(args: Array[String]): Unit = {
       |    def outer(): Unit = {
       |      val s = "start"
       |      def inner(a: String, b: String): String = {
       |        println()$bp
       |        s + a + b
       |      }
       |      inner("aa", "bb")
       |    }
       |    outer()
       |  }
       |}
    """.stripMargin.trim()
  )

  @Test
  def testClosure(): Unit = {
    runDebugger() {
      waitForBreakpoint()
      evalEquals("a", "aa")
      evalEquals("b", "bb")
      evalEquals("s", "start")
      evalEquals("inner(\"qq\", \"ww\")", "startqqww")
    }
  }

  addFileWithBreakpoints("LocalWithDefaultAndNamedParams.scala",
    s"""
       |object LocalWithDefaultAndNamedParams {
       |  def main(args: Array[String]): Unit = {
       |    def outer(): Unit = {
       |      def inner(a: String, b: String = "default", c: String = "other"): String = {
       |        println()$bp
       |        a + b + c
       |      }
       |      inner("aa")
       |    }
       |    outer()
       |  }
       |}
    """.stripMargin.trim()
  )

  @Test
  def testLocalWithDefaultAndNamedParams(): Unit = {
    runDebugger() {
      waitForBreakpoint()
      evalEquals("a", "aa")
      evalEquals("b", "default")
      evalEquals("c", "other")
      evalEquals("inner(\"aa\", \"bb\")", "aabbother")
      evalEquals("inner(\"aa\")", "aadefaultother")
      evalEquals("inner(\"aa\", c = \"cc\")", "aadefaultcc")
    }
  }

  addFileWithBreakpoints("LocalMethodsWithSameName.scala",
    s"""
       |object LocalMethodsWithSameName {
       |  def main(args: Array[String]): Unit = {
       |    def foo(i: Int = 1) = {
       |      def foo(j: Int = 2) = j
       |      i$bp
       |    }
       |    println()$bp
       |    def other(): Unit = {
       |      def foo(i: Int = 3) = i
       |      println()$bp
       |    }
       |    def third(): Unit = {
       |      def foo(i: Int = 4) = i
       |      println()$bp
       |    }
       |    foo()
       |    other()
       |    third()
       |  }
       |}
    """.stripMargin.trim())

  @Test
  def testLocalMethodsWithSameName(): Unit = {
    runDebugger() {
      waitForBreakpoint()
      evalEquals("foo()", "1")
      atNextBreakpoint {
        evalEquals("foo()", "2")
      }
      atNextBreakpoint {
        evalEquals("foo()", "3")
      }
      atNextBreakpoint {
        evalEquals("foo()", "4")
      }
    }
  }

  addFileWithBreakpoints("LocalMethodsWithSameName1.scala",
    s"""
       |object LocalMethodsWithSameName1 {
       |  def main(args: Array[String]): Unit = {
       |    def foo(i: Int = 1, u: Int = 10, v: Long = 100) = {
       |      def foo(j: Int = 2) = 20 + j
       |      1000 + i + u + v$bp
       |    }
       |    println()$bp
       |    def other(): Unit = {
       |      def foo(i: Int = 3, u: Int = 30, v: Long = 300) = 3000 + i + u + v
       |      println()$bp
       |    }
       |    def third(): Unit = {
       |      def foo(i: Int, u: Int = 40, v: Long = 400) = 4000 + i + u + v
       |      println()$bp
       |    }
       |    foo()
       |    other()
       |    third()
       |  }
       |}
    """.stripMargin.trim())

  @Test
  def testLocalMethodsWithSameName1(): Unit = {
    runDebugger() {
      waitForBreakpoint()
      evalEquals("foo()", "1111")
      atNextBreakpoint {
        evalEquals("foo()", "22")
      }
      atNextBreakpoint {
        evalEquals("foo()", "3333")
      }
      atNextBreakpoint {
        evalEquals("foo(4)", "4444")
      }
    }
  }

  addFileWithBreakpoints("LocalMethodsWithSameName2.scala",
    s"""
       |object LocalMethodsWithSameName2 {
       |  def main(args: Array[String]): Unit = {
       |    def foo(i: Int = 1, u: Int = 10, v: Long = 100) = {
       |      def foo(j: Int = 2, u: Int = 20) = 200 + j + u
       |      1000 + i + u + v$bp
       |    }
       |    println()$bp
       |    foo()
       |  }
       |}
    """.stripMargin.trim())

  @Test
  def testLocalMethodsWithSameName2(): Unit = {
    runDebugger() {
      waitForBreakpoint()
      evalEquals("foo()", "1111")
      atNextBreakpoint {
        evalEquals("foo()", "222")
      }
    }
  }

  addFileWithBreakpoints("ClosureWithDefaultParameter.scala",
    s"""
       |object ClosureWithDefaultParameter {
       |  def main(args: Array[String]): Unit = {
       |    def outer(): Unit = {
       |      val s = "start"
       |      val d = "default"
       |      def inner(a: String, b: String = d): String = {
       |        println()$bp
       |        s + a + b
       |      }
       |      inner("aa")
       |    }
       |    outer()
       |  }
       |}
    """.stripMargin.trim()
  )

  @Test
  def testClosureWithDefaultParameter(): Unit = {
    runDebugger() {
      waitForBreakpoint()
      evalEquals("a", "aa")
      evalEquals("b", "default")
      evalEquals("s", "start")
      evalEquals("inner(\"aa\", \"bb\")", "startaabb")
      evalEquals("inner(\"aa\")", "startaadefault")
    }
  }

  addFileWithBreakpoints("FunctionsWithLocalParameters.scala",
    s"""
       |object FunctionsWithLocalParameters {
       |  def main(args: Array[String]): Unit = {
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
       |        println()$bp
       |        z
       |      }
       |      inner("aa")
       |    }
       |    outer()
       |  }
       |}
    """.stripMargin.trim()
  )

  @Test
  @Category(Array(classOf[FlakyTests]))
  def testFunctionsWithLocalParameters(): Unit = {
    runDebugger() {
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

  addFileWithBreakpoints("WithFieldsFromOtherThread.scala",
    s"""object WithFieldsFromOtherThread {
        |  val field = "field"
        |  def main(args: Array[String]): Unit = {
        |    def localFun1() = "localFun1"
        |
        |    val inMain = "inMain"
        |    val inMainNotUsed = ":("
        |    inOtherThread {
        |      def localFun2 = "localFun2"
        |
        |      val inFirst = "inFirst"
        |      var inFirstVar = "inFirstVar"
        |      val inFirstVarNotUsed = ":("
        |      inOtherThread {
        |        val local = "local"
        |        inMain + inFirst + inFirstVar
        |        println()$bp
        |      }
        |    }
        |  }
        |
        |  def inOtherThread(action: => Unit) = {
        |    new Thread {
        |      override def run(): Unit = action
        |    }.start()
        |  }
        |}
    """.stripMargin.trim)

  @Test
  @Category(Array(classOf[FlakyTests]))
  def testWithFieldsFromOtherThread(): Unit = {
    runDebugger() {
      waitForBreakpoint()
      evalEquals("field", "field")
      evalEquals("inMain", "inMain")
      evalEquals("inFirst", "inFirst")
      evalEquals("inFirstVar", "inFirstVar")
      evalEquals("local", "local")
      evalEquals("localFun2", "localFun2")
      evalEquals("localFun1()", "localFun1")
    }
  }

  addFileWithBreakpoints("InForStmt.scala",
    s"""
      |object InForStmt {
      |  def main(args: Array[String]): Unit = {
      |    for {
      |      x <- Seq(1, 2)
      |      x1 = x + 1
      |      y <- Seq(3, 4)
      |      y1 = y + 1
      |      if x == 1 && y == 3
      |    } {
      |      class Inner {
      |        def foo = x$bp
      |      }
      |      def getX = x
      |      def getX1 = x1
      |      def getY = y
      |      def getY1 = y1
      |      new Inner().foo
      |      println()$bp
      |    }
      |  }
      |}
    """.stripMargin.trim)

  @Test
  def testInForStmt(): Unit = {
    runDebugger() {
      waitForBreakpoint()
      evalEquals("getX", "1")
      evalEquals("getX1", "2")
      //SCL-17927
      //evalEquals("getY()", "3")
      //evalEquals("getY1", "4")
      evalEquals("new Inner().foo", "1")
      atNextBreakpoint {
        evalEquals("getX", "1")
        evalEquals("getX1", "2")
        //evalEquals("getY()", "3")
        //evalEquals("getY1", "4")
        evalEquals("new Inner().foo", "1")
      }
    }
  }

  addFileWithBreakpoints("QualifierNamedAsPackage.scala",
    s"""
       |object QualifierNamedAsPackage {
       |  def main(args: Array[String]): Unit = {
       |    val invoke = "invoke"
       |    val text = "text"
       |    val ref = "ref"
       |    println()$bp
       |  }
       |}
    """.stripMargin.trim)

  @Test
  def testQualifierNamedAsPackage(): Unit = {
    runDebugger() {
      waitForBreakpoint()
      evalEquals("invoke.charAt(0)", "i")
      evalEquals("text.length", "4")
      evalEquals("ref.isEmpty()", "false")
      evalEquals("ref + text", "reftext")
    }
  }

  addFileWithBreakpoints("DefaultArgsInTrait.scala",
    s"""object DefaultArgsInTrait extends SomeTrait {
       |  def main(args: Array[String]): Unit = {
       |    traitMethod("", true)
       |  }
       |
       |}
       |
       |trait SomeTrait {
       |  def traitMethod(s: String, firstArg: Boolean = false): String = {
       |    def local(firstArg: Boolean = false, secondArg: Boolean = false): String = {
       |      if (firstArg) "1"
       |      else if (secondArg) "2"
       |      else "0"
       |    }
       |    "stop here"$bp
       |    local(firstArg)
       |  }
       |}
   """.stripMargin.trim)

  @Test
  def testDefaultArgsInTrait(): Unit = {
    runDebugger() {
      waitForBreakpoint()
      evalEquals("local()", "0")
      evalEquals("local(false)", "0")
      evalEquals("local(false, true)", "2")
      evalEquals("local(secondArg = true)", "2")
      evalEquals("local(firstArg = firstArg)", "1")
      evalEquals("""traitMethod("")""", "0")
      evalEquals("""traitMethod("", true)""", "1")
      evalEquals("""traitMethod("", firstArg = false)""", "0")
    }
  }
}