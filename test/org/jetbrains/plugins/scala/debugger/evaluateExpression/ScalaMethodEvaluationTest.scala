package org.jetbrains.plugins.scala.debugger.evaluateExpression

import org.jetbrains.plugins.scala.debugger.{ScalaDebuggerTestCase, ScalaVersion_2_11, ScalaVersion_2_12, ScalaVersion_2_12_OLD}


/**
 * User: Alefas
 * Date: 17.10.11
 */

class ScalaMethodEvaluationTest extends ScalaMethodEvaluationTestBase with ScalaVersion_2_11

class ScalaMethodEvaluationTest_212 extends ScalaMethodEvaluationTestBase with ScalaVersion_2_12

class ScalaMethodEvaluationTest_212_OLD extends ScalaMethodEvaluationTestBase with ScalaVersion_2_12_OLD

abstract class ScalaMethodEvaluationTestBase extends ScalaDebuggerTestCase {
  
  addFileWithBreakpoints("SmartBoxing.scala",
   s"""
      |object SmartBoxing {
      |  def foo(x: AnyVal) = 1
      |  def goo(x: Int) = x + 1
      |  def main(args: Array[String]) {
      |    val z = java.lang.Integer.valueOf(5)
      |    ""$bp
      |  }
      |}
    """.stripMargin.trim()
  )
  def testSmartBoxing() {
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
      |  def main(args: Array[String]) {
      |    ""$bp
      |  }
      |}
    """.stripMargin.trim()
  )
  def testFunctionWithSideEffects() {
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
      |  def main(args: Array[String]) {
      |    ""$bp
      |  }
      |}
    """.stripMargin.trim()
  )
  def testSimpleFunction() {
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
      |  def main(args: Array[String]) {
      |    ""$bp
      |  }
      |}
      |class PrivateMethods {
      |  private def bar() = 1
      |}
    """.stripMargin.trim()
  )
  def testPrivateMethods() {
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
      |  def main(args : Array[String]) {
      |    val a = new A()
      |    ""$bp
      |  }
      |}
    """.stripMargin.trim()
  )
  def testApplyCall() {
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
      |  def main(args: Array[String]) {
      |    ""$bp
      |  }
      |}
    """.stripMargin.trim()
  )
  def testCurriedFunction() {
    runDebugger() {
      waitForBreakpoint()
      evalEquals("foo(1)(2)", "4")
    }
  }

  addFileWithBreakpoints("ArrayApplyFunction.scala",
   s"""
      |object ArrayApplyFunction {
      |  def main(args : Array[String]) {
      |    val s = Array.ofDim[String](2, 2)
      |    s(1)(1) = "test"
      |    ""$bp
      |  }
      |}
    """.stripMargin.trim()
  )
  def testArrayApplyFunction() {
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
      |  def main(args: Array[String]) {
      |    ""$bp
      |  }
      |}
    """.stripMargin.trim()
  )
  def testOverloadedFunction() {
    runDebugger() {
      waitForBreakpoint()
      evalEquals("foo(1)", "1")
      evalEquals("foo(\"\")", "2")
    }
  }

  addFileWithBreakpoints("ImplicitConversion.scala",
   s"""
      |object ImplicitConversion {
      |  def main(args : Array[String]) {
      |    ""$bp
      |  }
      |}
    """.stripMargin.trim()
  )
  def testImplicitConversion() {
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
      |  def main(args: Array[String]) {
      |    val x = Seq("a", "b")
      |    ""$bp
      |  }
      |}
    """.stripMargin.trim()
  )
  def testSequenceArgument() {
    runDebugger() {
      waitForBreakpoint()
      evalEquals("moo(x: _*)", "2")
    }
  }

  addFileWithBreakpoints("ArrayLengthFunction.scala",
   s"""
      |object ArrayLengthFunction {
      |  def main(args : Array[String]) {
      |    val s = Array(1, 2, 3)
      |    ""$bp
      |  }
      |}
    """.stripMargin.trim()
  )
  def testArrayLengthFunction() {
    runDebugger() {
      waitForBreakpoint()
      evalEquals("s.length", "3")
    }
  }

  addFileWithBreakpoints("SimpleFunctionFromInner.scala",
   s"""
      |object SimpleFunctionFromInner {
      |  def foo() = 2
      |  def main(args: Array[String]) {
      |    val x = 1
      |    val r = () => {
      |      x
      |      ""$bp
      |    }
      |    r()
      |  }
      |}
    """.stripMargin.trim()
  )
  def testSimpleFunctionFromInner() {
    runDebugger() {
      waitForBreakpoint()
      evalEquals("foo", "2")
    }
  }

  addFileWithBreakpoints("LibraryFunctions.scala",
   s"""
      |object LibraryFunctions {
      |  def main(args: Array[String]) {
      |    ""$bp
      |  }
      |}
    """.stripMargin.trim()
  )
  def testLibraryFunctions() {
    runDebugger() {
      waitForBreakpoint()
      evalStartsWith("scala.collection.mutable.ArrayBuffer.newBuilder", "ArrayBuffer()")
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
       |  def main(args: Array[String]) {
       |    val a: A = new B
       |    ""$bp
       |  }
       |}
      """.stripMargin.trim()
  )
  def testDynamicFunctionApplication() {
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
      |  def main(args: Array[String]) {
      |    def moo() {}
      |    class A {
      |      val x = 1
      |      def goo() = 2
      |      def foo() {
      |        val r = () => {
      |          moo()
      |          x
      |          ""$bp
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
  def testNonStaticFunction() {
    runDebugger() {
      waitForBreakpoint()
      evalStartsWith("goo", "2")
    }
  }

  addFileWithBreakpoints("DefaultAndNamedParameters.scala",
   s"""
      |object DefaultAndNamedParameters {
      |  def foo(x: Int, y: Int = 1, z: Int)(h: Int = x + y, m: Int) = x + y + z + h + m
      |  def main(args: Array[String]) {
      |    ""$bp
      |  }
      |}
    """.stripMargin.trim()
  )
  def testDefaultAndNamedParameters() {
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
      |  def main(args: Array[String]) {
      |    ""$bp
      |  }
      |}
    """.stripMargin.trim()
  )
  def testRepeatedParameters() {
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
      |    ""$bp
      |    x + y
      |  }
      |  def main(args: Array[String]) {
      |    implicit val x = 1
      |    foo(1)
      |  }
      |}
    """.stripMargin.trim()
  )
  def testImplicitParameters() {
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
      |  def main(args: Array[String]) {
      |    ""$bp
      |  }
      |}
    """.stripMargin.trim()
  )
  def testCaseClasses() {
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
      |    ""$bp
      |  }
      |}
      |
      |object PrivateInTrait {
      |  class A extends Privates
      |
      |  def main(args: Array[String]) {
      |    val a = new A
      |    a.open()
      |  }
      |}""".stripMargin)
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
      |  def main(args: Array[String]) {
      |    val a = new A
      |    a.foo()
      |  }
      |}
    """.stripMargin)
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
       |  def main(args: Array[String]) {
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
       |  def simple() {
       |    def foo1: Int = 1
       |    ""$bp
       |  }
       |
       |  def withParameters() {
       |    val y = "test"
       |    def foo2(x: Int): Int = x + y.length
       |    ""$bp
       |  }
       |
       |  def withParamFromLocal() {
       |    val x = 2
       |    def foo3: Int = x - 1
       |    ""$bp
       |  }
       |
       |  def withDiffParams1() {
       |    val x = 2
       |    val y = "c"
       |    def foo4: Int = x - y.length()
       |    ""$bp
       |  }
       |
       |  def withDiffParams2() {
       |    val y = "c"
       |    val x = 2
       |    def foo5(): Int = x - y.length()
       |    ""$bp
       |  }
       |
       |  def withDiffParams3() {
       |    val y = "c"
       |    val x = 2
       |    def foo6: Int = - y.length() + x
       |    ""$bp
       |  }
       |
       |  def withObject() {
       |    object y {val y = 1}
       |    val x = 2
       |    def foo7: Int = x - y.y
       |    ""$bp
       |  }
       |
       |  def withAnonfunField() {
       |    val g = 1
       |    def moo(x: Int) = g + x
       |    val zz = (y: Int) => {
       |      val uu = (x: Int) => {
       |        g
       |        ""$bp
       |      }
       |      uu(1)
       |    }
       |    zz(2)
       |  }
       |
       |  def useField() {
       |    val x = 2
       |    def foo8: Int = x - field
       |    ""$bp
       |  }
       |}
    """.stripMargin.trim()
  )

  def testLocalFunctions() {
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
       |  def main(args: Array[String]) {
       |    def outer() {
       |      val s = "start"
       |      def inner(a: String, b: String): String = {
       |        ""$bp
       |        s + a + b
       |      }
       |      inner("aa", "bb")
       |    }
       |    outer()
       |  }
       |}
    """.stripMargin.trim()
  )

  def testClosure() {
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
       |  def main(args: Array[String]) {
       |    def outer() {
       |      def inner(a: String, b: String = "default", c: String = "other"): String = {
       |        ""$bp
       |        a + b + c
       |      }
       |      inner("aa")
       |    }
       |    outer()
       |  }
       |}
    """.stripMargin.trim()
  )

  def testLocalWithDefaultAndNamedParams() {
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
       |  def main(args: Array[String]) {
       |    def foo(i: Int = 1) = {
       |      def foo(j: Int = 2) = j
       |      i$bp
       |    }
       |    ""$bp
       |    def other() {
       |      def foo(i: Int = 3) = i
       |      ""$bp
       |    }
       |    def third() {
       |      def foo(i: Int = 4) = i
       |      ""$bp
       |    }
       |    foo()
       |    other()
       |    third()
       |  }
       |}
    """.stripMargin.trim())

  def testLocalMethodsWithSameName() {
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

  addFileWithBreakpoints("ClosureWithDefaultParameter.scala",
    s"""
       |object ClosureWithDefaultParameter {
       |  def main(args: Array[String]) {
       |    def outer() {
       |      val s = "start"
       |      val d = "default"
       |      def inner(a: String, b: String = d): String = {
       |        ""$bp
       |        s + a + b
       |      }
       |      inner("aa")
       |    }
       |    outer()
       |  }
       |}
    """.stripMargin.trim()
  )

  def testClosureWithDefaultParameter() {
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
       |        ""$bp
       |        z
       |      }
       |      inner("aa")
       |    }
       |    outer()
       |  }
       |}
    """.stripMargin.trim()
  )

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
        |  def main(args: Array[String]) {
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
        |        ""$bp
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
      |  def main(args: Array[String]) {
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
      |      ""$bp
      |    }
      |  }
      |}
    """.stripMargin.trim)

  def testInForStmt(): Unit = {
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

  addFileWithBreakpoints("QualifierNamedAsPackage.scala",
    s"""
       |object QualifierNamedAsPackage {
       |  def main(args: Array[String]) {
       |    val invoke = "invoke"
       |    val text = "text"
       |    val ref = "ref"
       |    ""$bp
       |  }
       |}
    """.stripMargin.trim)
  def testQualifierNamedAsPackage(): Unit = {
    runDebugger() {
      waitForBreakpoint()
      evalEquals("invoke.charAt(0)", "i")
      evalEquals("text.length", "4")
      evalEquals("ref.isEmpty()", "false")
      evalEquals("ref + text", "reftext")
    }
  }
}