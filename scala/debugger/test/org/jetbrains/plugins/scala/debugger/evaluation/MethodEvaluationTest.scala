package org.jetbrains.plugins.scala
package debugger
package evaluation

class MethodEvaluationTest_2_11 extends MethodEvaluationTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_2_11

  //todo move to ScalaMethodEvaluationTestBase when SCL-17927 is fixed
  override def testInForStmt(): Unit = {
    expressionEvaluationTest()(
      implicit ctx => {
        evalEquals("getX", "1")
        evalEquals("getX1", "2")
        evalEquals("getY()", "3")
        evalEquals("getY1", "4")
        evalEquals("new Inner().foo", "1")
      },
      implicit ctx => {
        evalEquals("getX", "1")
        evalEquals("getX1", "2")
        evalEquals("getY()", "3")
        evalEquals("getY1", "4")
        evalEquals("new Inner().foo", "1")
      }
    )
  }
}

class MethodEvaluationTest_2_12 extends MethodEvaluationTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_2_12
}

class MethodEvaluationTest_2_13 extends MethodEvaluationTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_2_13
}

class MethodEvaluationTest_3_0 extends MethodEvaluationTestBase {
  override protected def supportedIn(version: ScalaVersion) = version == ScalaVersion.Latest.Scala_3_0

  addSourceFile("one.scala", "def one() = 1")
  addSourceFile("a/two.scala",
    """package a
      |
      |def two() = 2
      |""".stripMargin.trim)
  addSourceFile("a/b/three.scala",
    """package a.b
      |
      |def three =
      |  3
      |""".stripMargin.trim)
  addSourceFile("topLevel.scala",
    s"""import a.two
       |import a.b.three
       |
       |@main
       |def TopLevel(): Unit =
       |  def local() = "local"
       |  println() $breakpoint
       |
       |private val nineVar = 9
       |private val tenVal = 10
       |private def fortyTwo(): Int = 42
       |""".stripMargin.trim)

  def testTopLevel(): Unit = {
    expressionEvaluationTest() { implicit ctx =>
      evalEquals("one()", "1")
      evalEquals("two()", "2")
      evalEquals("three", "3")
      evalEquals("nineVar", "9")
      evalEquals("tenVal", "10")
      evalEquals("fortyTwo()", "42")
      evalEquals("local()", "local")

      // Bad syntax:
      // Yes, in Scala 3 this is no a valid syntax.
      // But why not to support it in debugger? The methods exist in the runtime anyway.
      evalEquals("one", "1")
      evalEquals("two", "2")
      evalEquals("three()", "3")
      evalEquals("nineVar()", "9")
      evalEquals("tenVal()", "10")
      evalEquals("fortyTwo", "42")
      evalEquals("local", "local")
    }
  }

  override def testClosureWithDefaultParameter(): Unit = {
    expressionEvaluationTest() { implicit ctx =>
      evalEquals("a", "aa")
      evalEquals("b", "default")
      evalEquals("s", "start")
      failing(evalEquals("inner(\"aa\", \"bb\")", "startaabb"))
      failing(evalEquals("inner(\"aa\")", "startaadefault"))
    }
  }

  override def testNonStaticFunction(): Unit = {
    expressionEvaluationTest() { implicit ctx =>
      failing(evalStartsWith("goo", "2"))
    }
  }

  override def testClosure(): Unit = {
    expressionEvaluationTest() { implicit ctx =>
      evalEquals("a", "aa")
      evalEquals("b", "bb")
      evalEquals("s", "start")
      failing(evalEquals("inner(\"qq\", \"ww\")", "startqqww"))
    }
  }

  override def testFunctionsWithLocalParameters(): Unit = {
    expressionEvaluationTest() { implicit ctx =>
      evalEquals("a", "aa")
      evalEquals("b", "default")
      evalEquals("x", "1")
      evalEquals("y", "2")
      evalEquals("s", "start")
      evalEquals("z", "startaadefault2")
      evalEquals("inInner()", "startaadefault21")
      evalEquals("inner(\"aa\", \"bb\")", "startaabb2")
      failing(evalEquals("inner(\"aa\")", "startaadefault2"))
      failing(evalEquals("outer()", "startaadefault2"))
    }
  }

  override def testLocalFunctions(): Unit = {
    expressionEvaluationTest()(
      implicit ctx => evalEquals("foo1", "1"),
      implicit ctx => evalEquals("foo2(3)", "7"),
      implicit ctx => evalEquals("foo3", "1"),
      implicit ctx => evalEquals("foo4", "1"),
      implicit ctx => failing(evalEquals("foo5", "1")),
      implicit ctx => evalEquals("foo6", "1"),
      implicit ctx => failing(evalEquals("foo7", "1")),
      implicit ctx => evalEquals("moo(x)", "2"),
      implicit ctx => evalEquals("foo8", "1")
    )
  }

  override def testLocalMethodsWithSameName(): Unit = {
    expressionEvaluationTest()(
      implicit ctx => failing(evalEquals("foo()", "1")),
      implicit ctx => failing(evalEquals("foo()", "2")),
      implicit ctx => evalEquals("foo()", "3"),
      implicit ctx => evalEquals("foo()", "4")
    )
  }

  override def testLocalMethodsWithSameName1(): Unit = {
    expressionEvaluationTest()(
      implicit ctx => failing(evalEquals("foo()", "1111")),
      implicit ctx => failing(evalEquals("foo()", "22")),
      implicit ctx => evalEquals("foo()", "3333"),
      implicit ctx => evalEquals("foo(4)", "4444")
    )
  }

  override def testLocalMethodsWithSameName2(): Unit = {
    expressionEvaluationTest()(
      implicit ctx => failing(evalEquals("foo()", "1111")),
      implicit ctx => failing(evalEquals("foo()", "222"))
    )
  }
}

class MethodEvaluationTest_3_1 extends MethodEvaluationTest_3_0 {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_3_1

  override def testLocalMethodsWithSameName(): Unit = {
    expressionEvaluationTest()(
      implicit ctx => evalEquals("foo()", "1"),
      implicit ctx => evalEquals("foo()", "2"),
      implicit ctx => evalEquals("foo()", "3"),
      implicit ctx => evalEquals("foo()", "4")
    )
  }

  override def testLocalMethodsWithSameName1(): Unit = {
    expressionEvaluationTest()(
      implicit ctx => evalEquals("foo()", "1111"),
      implicit ctx => evalEquals("foo()", "22"),
      implicit ctx => evalEquals("foo()", "3333"),
      implicit ctx => evalEquals("foo(4)", "4444")
    )
  }

  override def testLocalMethodsWithSameName2(): Unit = {
    expressionEvaluationTest()(
      implicit ctx => evalEquals("foo()", "1111"),
      implicit ctx => evalEquals("foo()", "222")
    )
  }
}

class MethodEvaluationTest_3 extends MethodEvaluationTest_3_1 {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_3

  override def testFunctionsWithLocalParameters(): Unit = {
    expressionEvaluationTest() { implicit ctx =>
      evalEquals("a", "aa")
      evalEquals("b", "default")
      evalEquals("x", "1")
      evalEquals("y", "2")
      evalEquals("s", "start")
      evalEquals("z", "startaadefault2")
      evalEquals("inInner()", "startaadefault21")
      evalEquals("inner(\"aa\", \"bb\")", "startaabb2")
      failing(evalEquals("inner(\"aa\")", "startaadefault2"))
      evalEquals("outer()", "startaadefault2")
    }
  }

  override def testLocalFunctions(): Unit = {
    expressionEvaluationTest()(
      implicit ctx => evalEquals("foo1", "1"),
      implicit ctx => evalEquals("foo2(3)", "7"),
      implicit ctx => evalEquals("foo3", "1"),
      implicit ctx => evalEquals("foo4", "1"),
      implicit ctx => evalEquals("foo5", "1"),
      implicit ctx => evalEquals("foo6", "1"),
      implicit ctx => failing(evalEquals("foo7", "1")),
      implicit ctx => evalEquals("moo(x)", "2"),
      implicit ctx => evalEquals("foo8", "1")
    )
  }
}

class MethodEvaluationTest_3_RC extends MethodEvaluationTest_3 {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_3_RC

  override def testNonStaticFunction(): Unit = {
    expressionEvaluationTest() { implicit ctx =>
      evalStartsWith("goo", "2")
    }
  }
}

abstract class MethodEvaluationTestBase extends ExpressionEvaluationTestBase {
  addSourceFile("SmartBoxing.scala",
    s"""
       |object SmartBoxing {
       |  def foo(x: AnyVal) = 1
       |  def goo(x: Int) = x + 1
       |  def main(args: Array[String]): Unit = {
       |    val z = java.lang.Integer.valueOf(5)
       |    println() $breakpoint
       |  }
       |}
    """.stripMargin.trim
  )

  def testSmartBoxing(): Unit = {
    expressionEvaluationTest() { implicit ctx =>
      evalEquals("foo(1)", "1")
      evalEquals("goo(z)", "6")
    }
  }

  addSourceFile("FunctionWithSideEffects.scala",
    s"""
       |object FunctionWithSideEffects {
       |  var i = 1
       |  def foo = {
       |    i = i + 1
       |    i
       |  }
       |  def main(args: Array[String]): Unit = {
       |    println() $breakpoint
       |  }
       |}
    """.stripMargin.trim
  )

  def testFunctionWithSideEffects(): Unit = {
    expressionEvaluationTest() { implicit ctx =>
      evalEquals("foo", "2")
      evalEquals("foo", "3")
    }
  }

  addSourceFile("SimpleFunction.scala",
    s"""
       |object SimpleFunction {
       |  def foo() = 2
       |  def main(args: Array[String]): Unit = {
       |    println() $breakpoint
       |  }
       |}
    """.stripMargin.trim
  )

  def testSimpleFunction(): Unit = {
    expressionEvaluationTest() { implicit ctx =>
      evalEquals("foo", "2")
    }
  }

  addSourceFile("PrivateMethods.scala",
    s"""
       |import PrivateMethods._
       |object PrivateMethods {
       |  private def foo() = 2
       |  def main(args: Array[String]): Unit = {
       |    println() $breakpoint
       |  }
       |}
       |class PrivateMethods {
       |  private def bar() = 1
       |}
    """.stripMargin.trim
  )

  def testPrivateMethods(): Unit = {
    expressionEvaluationTest() { implicit ctx =>
      evalEquals("foo", "2")
      evalEquals("new PrivateMethods().bar()", "1")
    }
  }

  addSourceFile("ApplyCall.scala",
    s"""
       |object ApplyCall {
       |  class A {
       |    def apply(x: Int) = x + 1
       |  }
       |  def main(args: Array[String]): Unit = {
       |    val a = new A()
       |    println() $breakpoint
       |  }
       |}
    """.stripMargin.trim
  )

  def testApplyCall(): Unit = {
    expressionEvaluationTest() { implicit ctx =>
      evalEquals("a(-1)", "0")
      evalEquals("Array(\"a\", \"b\")", "[a,b]")
    }
  }

  addSourceFile("CurriedFunction.scala",
    s"""
       |object CurriedFunction {
       |  def foo(x: Int)(y: Int) = x * 2 + y
       |  def main(args: Array[String]): Unit = {
       |    println() $breakpoint
       |  }
       |}
    """.stripMargin.trim
  )

  def testCurriedFunction(): Unit = {
    expressionEvaluationTest() { implicit ctx =>
      evalEquals("foo(1)(2)", "4")
    }
  }

  addSourceFile("ArrayApplyFunction.scala",
    s"""
       |object ArrayApplyFunction {
       |  def main(args: Array[String]): Unit = {
       |    val s = Array.ofDim[String](2, 2)
       |    s(1)(1) = "test"
       |    println() $breakpoint
       |  }
       |}
    """.stripMargin.trim
  )

  def testArrayApplyFunction(): Unit = {
    expressionEvaluationTest() { implicit ctx =>
      evalEquals("s(1)(1)", "test")
    }
  }

  addSourceFile("OverloadedFunction.scala",
    s"""
       |object OverloadedFunction {
       |  def foo(x: Int) = 1
       |  def foo(x: String) = 2
       |  def main(args: Array[String]): Unit = {
       |    println() $breakpoint
       |  }
       |}
    """.stripMargin.trim
  )

  def testOverloadedFunction(): Unit = {
    expressionEvaluationTest() { implicit ctx =>
      evalEquals("foo(1)", "1")
      evalEquals("foo(\"\")", "2")
    }
  }

  addSourceFile("ImplicitConversion.scala",
    s"""
       |object ImplicitConversion {
       |  def main(args: Array[String]): Unit = {
       |    println() $breakpoint
       |  }
       |}
    """.stripMargin.trim
  )

  def testImplicitConversion(): Unit = {
    expressionEvaluationTest() { implicit ctx =>
      evalEquals("\"test\".dropRight(2)", "te")
      evalEquals("\"3\" -> \"3\"", "(3,3)")
      evalEquals("(1 - 3).abs", "2")
    }
  }

  addSourceFile("SequenceArgument.scala",
    s"""
       |object SequenceArgument {
       |  def moo(x: String*) = x.foldLeft(0)(_ + _.length())
       |  def main(args: Array[String]): Unit = {
       |    val x = Seq("a", "b")
       |    println() $breakpoint
       |  }
       |}
    """.stripMargin.trim
  )

  def testSequenceArgument(): Unit = {
    expressionEvaluationTest() { implicit ctx =>
      evalEquals("moo(x: _*)", "2")
    }
  }

  addSourceFile("ArrayLengthFunction.scala",
    s"""
       |object ArrayLengthFunction {
       |  def main(args: Array[String]): Unit = {
       |    val s = Array(1, 2, 3)
       |    println() $breakpoint
       |  }
       |}
    """.stripMargin.trim
  )

  def testArrayLengthFunction(): Unit = {
    expressionEvaluationTest() { implicit ctx =>
      evalEquals("s.length", "3")
    }
  }

  addSourceFile("SimpleFunctionFromInner.scala",
    s"""
       |object SimpleFunctionFromInner {
       |  def foo() = 2
       |  def main(args: Array[String]): Unit = {
       |    val x = 1
       |    val r = () => {
       |      x
       |      println() $breakpoint
       |    }
       |    r()
       |  }
       |}
    """.stripMargin.trim
  )

  def testSimpleFunctionFromInner(): Unit = {
    expressionEvaluationTest() { implicit ctx =>
      evalEquals("foo", "2")
    }
  }

  addSourceFile("LibraryFunctions.scala",
    s"""
       |object LibraryFunctions {
       |  def main(args: Array[String]): Unit = {
       |    println() $breakpoint
       |  }
       |}
    """.stripMargin.trim
  )

  def testLibraryFunctions(): Unit = {
    expressionEvaluationTest() { implicit ctx =>
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

  addSourceFile("DynamicFunctionApplication.scala",
    s"""
       |class A
       |class B extends A {
       |  def foo() = 1
       |  def bar(s: String) = s
       |}
       |object DynamicFunctionApplication {
       |  def main(args: Array[String]): Unit = {
       |    val a: A = new B
       |    println() $breakpoint
       |  }
       |}
      """.stripMargin.trim
  )

  def testDynamicFunctionApplication(): Unit = {
    expressionEvaluationTest() { implicit ctx =>
      evalEquals("a.foo()", "1")
      evalEquals("a.bar(\"hi\")", "hi")
    }
  }

  addSourceFile("NonStaticFunction.scala",
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
       |          println() $breakpoint
       |        }
       |        r()
       |      }
       |    }
       |
       |    new A().foo()
       |  }
       |}
    """.stripMargin.trim
  )

  def testNonStaticFunction(): Unit = {
    expressionEvaluationTest() { implicit ctx =>
      evalStartsWith("goo", "2")
    }
  }

  addSourceFile("DefaultAndNamedParameters.scala",
    s"""
       |object DefaultAndNamedParameters {
       |  def foo(x: Int, y: Int = 1, z: Int)(h: Int = x + y, m: Int) = x + y + z + h + m
       |  def main(args: Array[String]): Unit = {
       |    println() $breakpoint
       |  }
       |}
    """.stripMargin.trim
  )

  def testDefaultAndNamedParameters(): Unit = {
    expressionEvaluationTest() { implicit ctx =>
      evalEquals("foo(1, z = 1)(m = 1)", "6")
      evalEquals("foo(1, 2, 1)(m = 1)", "8")
      evalEquals("foo(1, 2, 1)(1, m = 1)", "6")
    }
  }

  addSourceFile("RepeatedParameters.scala",
    s"""
       |object RepeatedParameters {
       |  def foo(x: String*) = x.foldLeft("")(_ + _)
       |  def main(args: Array[String]): Unit = {
       |    println() $breakpoint
       |  }
       |}
    """.stripMargin.trim
  )

  def testRepeatedParameters(): Unit = {
    expressionEvaluationTest() { implicit ctx =>
      evalEquals("foo(\"a\", \"b\", \"c\")", "abc")
      evalEquals("foo(\"a\")", "a")
      evalEquals("foo()", "")
      evalEquals("Array[Byte](0, 1)", "[0,1]")
    }
  }

  addSourceFile("ImplicitParameters.scala",
    s"""
       |object ImplicitParameters {
       |  def moo(x: Int)(implicit s: String) = x + s.length()
       |  def foo(x: Int)(implicit y: Int) = {
       |    implicit val s = "test"
       |    println() $breakpoint
       |    x + y
       |  }
       |  def main(args: Array[String]): Unit = {
       |    implicit val x = 1
       |    foo(1)
       |  }
       |}
    """.stripMargin.trim
  )

  def testImplicitParameters(): Unit = {
    expressionEvaluationTest() { implicit ctx =>
      evalEquals("foo(1)", "2")
      evalEquals("foo(1)(2)", "3")
      evalEquals("moo(1)", "5")
      evalEquals("moo(1)(\"a\")", "2")
    }
  }

  addSourceFile("CaseClasses.scala",
    s"""
       |case class CCA(x: Int)
       |object CaseClasses {
       |  case class CCB(x: Int)
       |  def main(args: Array[String]): Unit = {
       |    println() $breakpoint
       |  }
       |}
    """.stripMargin.trim
  )

  def testCaseClasses(): Unit = {
    expressionEvaluationTest() { implicit ctx =>
      evalEquals("CCA(1)", "CCA(1)")
      evalEquals("CCA.apply(1)", "CCA(1)")
      evalEquals("CCB(1)", "CCB(1)")
      evalEquals("CCB.apply(1)", "CCB(1)")
    }
  }

  addSourceFile("PrivateInTrait.scala",
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
       |    println() $breakpoint
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

  def testPrivateInTrait(): Unit = {
    expressionEvaluationTest() { implicit ctx =>
      evalEquals("priv(0)", "2")
      evalEquals("privThis(0)", "1")
      evalEquals("privConst", "42")
    }
  }

  addSourceFile("LocalsInTrait.scala",
    s"""trait TTT {
       |  def foo() = {
       |    def bar() = {
       |      def baz() = 1
       |      baz() $breakpoint
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

  def testLocalsInTrait(): Unit = {
    expressionEvaluationTest() { implicit ctx =>
      evalEquals("bar()", "1")
      evalEquals("bar", "1")
      evalEquals("baz()", "1")
      evalEquals("foo()", "1")
      evalEquals("foo + bar", "2")
    }
  }

  // tests for local functions ----------------------------------------------

  addSourceFile("LocalFunctions.scala",
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
       |    println() $breakpoint
       |  }
       |
       |  def withParameters(): Unit = {
       |    val y = "test"
       |    def foo2(x: Int): Int = x + y.length
       |    println() $breakpoint
       |  }
       |
       |  def withParamFromLocal(): Unit = {
       |    val x = 2
       |    def foo3: Int = x - 1
       |    println() $breakpoint
       |  }
       |
       |  def withDiffParams1(): Unit = {
       |    val x = 2
       |    val y = "c"
       |    def foo4: Int = x - y.length()
       |    println() $breakpoint
       |  }
       |
       |  def withDiffParams2(): Unit = {
       |    val y = "c"
       |    val x = 2
       |    def foo5(): Int = x - y.length()
       |    println() $breakpoint
       |  }
       |
       |  def withDiffParams3(): Unit = {
       |    val y = "c"
       |    val x = 2
       |    def foo6: Int = - y.length() + x
       |    println() $breakpoint
       |  }
       |
       |  def withObject(): Unit = {
       |    object y {val y = 1}
       |    val x = 2
       |    def foo7: Int = x - y.y
       |    println() $breakpoint
       |  }
       |
       |  def withAnonfunField(): Unit = {
       |    val g = 1
       |    def moo(x: Int) = g + x
       |    val zz = (y: Int) => {
       |      val uu = (x: Int) => {
       |        g
       |        println() $breakpoint
       |      }
       |      uu(1)
       |    }
       |    zz(2)
       |  }
       |
       |  def useField(): Unit = {
       |    val x = 2
       |    def foo8: Int = x - field
       |    println() $breakpoint
       |  }
       |}
    """.stripMargin.trim
  )

  def testLocalFunctions(): Unit = {
    expressionEvaluationTest()(
      implicit ctx => evalEquals("foo1", "1"),
      implicit ctx => evalEquals("foo2(3)", "7"),
      implicit ctx => evalEquals("foo3", "1"),
      implicit ctx => evalEquals("foo4", "1"),
      implicit ctx => evalEquals("foo5", "1"),
      implicit ctx => evalEquals("foo6", "1"),
      implicit ctx => evalEquals("foo7", "1"),
      implicit ctx => evalEquals("moo(x)", "2"),
      implicit ctx => evalEquals("foo8", "1")
    )
  }

  addSourceFile("Closure.scala",
    s"""
       |object Closure {
       |  def main(args: Array[String]): Unit = {
       |    def outer(): Unit = {
       |      val s = "start"
       |      def inner(a: String, b: String): String = {
       |        println() $breakpoint
       |        s + a + b
       |      }
       |      inner("aa", "bb")
       |    }
       |    outer()
       |  }
       |}
    """.stripMargin.trim
  )

  def testClosure(): Unit = {
    expressionEvaluationTest() { implicit ctx =>
      evalEquals("a", "aa")
      evalEquals("b", "bb")
      evalEquals("s", "start")
      evalEquals("inner(\"qq\", \"ww\")", "startqqww")
    }
  }

  addSourceFile("LocalWithDefaultAndNamedParams.scala",
    s"""
       |object LocalWithDefaultAndNamedParams {
       |  def main(args: Array[String]): Unit = {
       |    def outer(): Unit = {
       |      def inner(a: String, b: String = "default", c: String = "other"): String = {
       |        println() $breakpoint
       |        a + b + c
       |      }
       |      inner("aa")
       |    }
       |    outer()
       |  }
       |}
    """.stripMargin.trim
  )

  def testLocalWithDefaultAndNamedParams(): Unit = {
    expressionEvaluationTest() { implicit ctx =>
      evalEquals("a", "aa")
      evalEquals("b", "default")
      evalEquals("c", "other")
      evalEquals("inner(\"aa\", \"bb\")", "aabbother")
      evalEquals("inner(\"aa\")", "aadefaultother")
      evalEquals("inner(\"aa\", c = \"cc\")", "aadefaultcc")
    }
  }

  addSourceFile("LocalMethodsWithSameName.scala",
    s"""
       |object LocalMethodsWithSameName {
       |  def main(args: Array[String]): Unit = {
       |    def foo(i: Int = 1) = {
       |      def foo(j: Int = 2) = j
       |      i $breakpoint
       |    }
       |    println() $breakpoint
       |    def other(): Unit = {
       |      def foo(i: Int = 3) = i
       |      println() $breakpoint
       |    }
       |    def third(): Unit = {
       |      def foo(i: Int = 4) = i
       |      println() $breakpoint
       |    }
       |    foo()
       |    other()
       |    third()
       |  }
       |}
    """.stripMargin.trim)

  def testLocalMethodsWithSameName(): Unit = {
    expressionEvaluationTest()(
      implicit ctx => evalEquals("foo()", "1"),
      implicit ctx => evalEquals("foo()", "2"),
      implicit ctx => evalEquals("foo()", "3"),
      implicit ctx => evalEquals("foo()", "4")
    )
  }

  addSourceFile("LocalMethodsWithSameName1.scala",
    s"""
       |object LocalMethodsWithSameName1 {
       |  def main(args: Array[String]): Unit = {
       |    def foo(i: Int = 1, u: Int = 10, v: Long = 100) = {
       |      def foo(j: Int = 2) = 20 + j
       |      1000 + i + u + v $breakpoint
       |    }
       |    println() $breakpoint
       |    def other(): Unit = {
       |      def foo(i: Int = 3, u: Int = 30, v: Long = 300) = 3000 + i + u + v
       |      println() $breakpoint
       |    }
       |    def third(): Unit = {
       |      def foo(i: Int, u: Int = 40, v: Long = 400) = 4000 + i + u + v
       |      println() $breakpoint
       |    }
       |    foo()
       |    other()
       |    third()
       |  }
       |}
    """.stripMargin.trim)

  def testLocalMethodsWithSameName1(): Unit = {
    expressionEvaluationTest()(
      implicit ctx => evalEquals("foo()", "1111"),
      implicit ctx => evalEquals("foo()", "22"),
      implicit ctx => evalEquals("foo()", "3333"),
      implicit ctx => evalEquals("foo(4)", "4444")
    )
  }

  addSourceFile("LocalMethodsWithSameName2.scala",
    s"""
       |object LocalMethodsWithSameName2 {
       |  def main(args: Array[String]): Unit = {
       |    def foo(i: Int = 1, u: Int = 10, v: Long = 100) = {
       |      def foo(j: Int = 2, u: Int = 20) = 200 + j + u
       |      1000 + i + u + v $breakpoint
       |    }
       |    println() $breakpoint
       |    foo()
       |  }
       |}
    """.stripMargin.trim)

  def testLocalMethodsWithSameName2(): Unit = {
    expressionEvaluationTest()(
      implicit ctx => evalEquals("foo()", "1111"),
      implicit ctx => evalEquals("foo()", "222")
    )
  }

  addSourceFile("ClosureWithDefaultParameter.scala",
    s"""
       |object ClosureWithDefaultParameter {
       |  def main(args: Array[String]): Unit = {
       |    def outer(): Unit = {
       |      val s = "start"
       |      val d = "default"
       |      def inner(a: String, b: String = d): String = {
       |        println() $breakpoint
       |        s + a + b
       |      }
       |      inner("aa")
       |    }
       |    outer()
       |  }
       |}
    """.stripMargin.trim
  )

  def testClosureWithDefaultParameter(): Unit = {
    expressionEvaluationTest() { implicit ctx =>
      evalEquals("a", "aa")
      evalEquals("b", "default")
      evalEquals("s", "start")
      evalEquals("inner(\"aa\", \"bb\")", "startaabb")
      evalEquals("inner(\"aa\")", "startaadefault")
    }
  }

  addSourceFile("FunctionsWithLocalParameters.scala",
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
       |        println() $breakpoint
       |        z
       |      }
       |      inner("aa")
       |    }
       |    outer()
       |  }
       |}
    """.stripMargin.trim
  )

  def testFunctionsWithLocalParameters(): Unit = {
    expressionEvaluationTest() { implicit ctx =>
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

  addSourceFile("WithFieldsFromOtherThread.scala",
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
       |        println() $breakpoint
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
    expressionEvaluationTest() { implicit ctx =>
      evalEquals("field", "field")
      evalEquals("inMain", "inMain")
      evalEquals("inFirst", "inFirst")
      evalEquals("inFirstVar", "inFirstVar")
      evalEquals("local", "local")
      evalEquals("localFun2", "localFun2")
      evalEquals("localFun1()", "localFun1")
    }
  }

  addSourceFile("InForStmt.scala",
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
       |        def foo = x $breakpoint
       |      }
       |      def getX = x
       |      def getX1 = x1
       |      def getY = y
       |      def getY1 = y1
       |      new Inner().foo
       |      println() $breakpoint
       |    }
       |  }
       |}
    """.stripMargin.trim)

  def testInForStmt(): Unit = {
    expressionEvaluationTest()(
      implicit ctx => {
        evalEquals("getX", "1")
        evalEquals("getX1", "2")
        //SCL-17927
        //evalEquals("getY()", "3")
        //evalEquals("getY1", "4")
        evalEquals("new Inner().foo", "1")
      },
      implicit ctx => {
        evalEquals("getX", "1")
        evalEquals("getX1", "2")
        //evalEquals("getY()", "3")
        //evalEquals("getY1", "4")
        evalEquals("new Inner().foo", "1")
      }
    )
  }

  addSourceFile("QualifierNamedAsPackage.scala",
    s"""
       |object QualifierNamedAsPackage {
       |  def main(args: Array[String]): Unit = {
       |    val invoke = "invoke"
       |    val text = "text"
       |    val ref = "ref"
       |    println() $breakpoint
       |  }
       |}
    """.stripMargin.trim)

  def testQualifierNamedAsPackage(): Unit = {
    expressionEvaluationTest() { implicit ctx =>
      evalEquals("invoke.charAt(0)", "i")
      evalEquals("text.length", "4")
      evalEquals("ref.isEmpty()", "false")
      evalEquals("ref + text", "reftext")
    }
  }

  addSourceFile("DefaultArgsInTrait.scala",
    s"""object DefaultArgsInTrait extends SomeTrait {
       |  def main(args: Array[String]): Unit = {
       |    traitMethod("", true)
       |  }
       |}
       |
       |trait SomeTrait {
       |  def traitMethod(s: String, firstArg: Boolean = false): String = {
       |    def local(firstArg: Boolean = false, secondArg: Boolean = false): String = {
       |      if (firstArg) "1"
       |      else if (secondArg) "2"
       |      else "0"
       |    }
       |    println() $breakpoint
       |    local(firstArg)
       |  }
       |}
   """.stripMargin.trim)

  def testDefaultArgsInTrait(): Unit = {
    expressionEvaluationTest() { implicit ctx =>
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
