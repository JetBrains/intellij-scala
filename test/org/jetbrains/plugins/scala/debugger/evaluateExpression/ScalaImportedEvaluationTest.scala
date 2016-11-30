package org.jetbrains.plugins.scala
package debugger.evaluateExpression

import org.jetbrains.plugins.scala.debugger.{ScalaDebuggerTestCase, ScalaVersion_2_11, ScalaVersion_2_12}

/**
  * Nikolay.Tropin
  * 12/9/13
  */

class ScalaImportedEvaluationTest extends ScalaImportedEvaluationTestBase with ScalaVersion_2_11

class ScalaImportedEvaluationTest_212 extends ScalaImportedEvaluationTestBase with ScalaVersion_2_12

abstract class ScalaImportedEvaluationTestBase extends ScalaDebuggerTestCase {
  addFileWithBreakpoints("ImportFromObject.scala",
    s"""
       |object ImportFromObject {
       |  def main(args: Array[String]) {
       |    import test.Stuff._
       |    ""$bp
       |  }
       |}
    """.stripMargin.trim()
  )
  addFileWithBreakpoints("test/Stuff.scala",
    s"""
       |package test
       |object Stuff {
       |  val x = 0
       |  def foo() = "foo"
       |}
    """.stripMargin.trim()
  )

  def testImportFromObject() {
    runDebugger() {
      waitForBreakpoint()
      evalEquals("x", "0")
      evalEquals("foo", "foo")
      evalEquals("foo()", "foo")
    }
  }

  addFileWithBreakpoints("ImportFromPackageObject.scala",
    s"""
       |object ImportFromPackageObject {
       |  def main(args: Array[String]) {
       |    import test.stuff._
       |    ""$bp
       |  }
       |}
    """.stripMargin.trim()
  )
  addFileWithBreakpoints("test/stuff/package.scala",
    s"""
       |package test
       |package object stuff {
       |  val x = 0
       |  def foo() = "foo"$bp
       |
       |  class AAA {
       |    val a = "a"
       |
       |    def bar() {
       |      ""$bp
       |    }
       |  }
       |
       |  implicit class ObjectExt[T](val v: T) extends AnyVal{
       |    def toOption: Option[T] = Option(v)$bp
       |  }
       |}
    """.stripMargin.trim()
  )

  def testImportFromPackageObject() {
    runDebugger() {
      waitForBreakpoint()
      evalEquals("x", "0")
      evalEquals("foo", "foo")
    }
  }

  addFileWithBreakpoints("StopInsidePackageObject.scala",
    s"""
       |object StopInsidePackageObject {
       |  def main(args: Array[String]) {
       |    import test.stuff._
       |    foo()
       |  }
       |}
    """.stripMargin.trim()
  )

  def testStopInsidePackageObject(): Unit = {
    runDebugger() {
      waitForBreakpoint()
      evalEquals("x", "0")
      evalEquals("foo", "foo")
    }
  }

  addFileWithBreakpoints("StopInsideClassInPackageObject.scala",
    s"""
       |object StopInsideClassInPackageObject {
       |  def main(args: Array[String]) {
       |    import test.stuff._
       |    new AAA().bar()
       |  }
       |}
    """.stripMargin.trim()
  )

  def testStopInsideClassInPackageObject(): Unit = {
    runDebugger() {
      waitForBreakpoint()
      evalEquals("x", "0")
      evalEquals("foo", "foo")
      evalEquals("a", "a")
    }
  }

  addFileWithBreakpoints("StopInsideValueClass.scala",
    s"""
       |object StopInsideValueClass {
       |  def main(args: Array[String]) {
       |    import test.stuff._
       |    "v".toOption
       |  }
       |}
    """.stripMargin.trim()
  )

  def testStopInsideValueClass(): Unit = {
    runDebugger() {
      waitForBreakpoint()
      evalEquals("x", "0")
      evalEquals("v", "v")
    }
  }

  addFileWithBreakpoints("ImportVal.scala",
    s"""
       |object ImportVal {
       |  def main(args: Array[String]) {
       |    val a = new A(0)
       |    import a._
       |    ""$bp
       |  }
       |}
       |
       |class A(val i: Int) {
       |  val x = 0
       |  def foo() = "foo"
       |}
    """.stripMargin.trim()
  )

  def testImportVal() {
    runDebugger() {
      waitForBreakpoint()
      evalEquals("x", "0")
      evalEquals("i", "0")
      evalEquals("foo", "foo")
    }
  }

  addFileWithBreakpoints("ImportProjectionType.scala",
    s"""
       |object ImportProjectionType {
       |
       |  def main(args: Array[String]) {
       |    new ImportProjectionType().foo()
       |  }
       |}
       |
       |class ImportProjectionType {
       |  class B {
       |    val y = Seq(1, 2, 3)
       |  }
       |
       |  val x = "abc"
       |
       |  def foo() {
       |    val b = new B
       |    import b.y._
       |    import x._
       |
       |    ""$bp
       |  }
       |
       |}
    """.stripMargin.trim()
  )

  def testImportProjectionType() {
    runDebugger() {
      waitForBreakpoint()
      evalEquals("charAt(0)", "a")
      evalEquals("head", "1")
    }
  }

  addFileWithBreakpoints("ImportJava.scala",
    s"""
       |object ImportJava {
       |  def main(args: Array[String]) {
       |    val jc = new test.JavaClass()
       |    import jc._
       |    import test.JavaClass._
       |
       |    val inner = new JavaInner()
       |    import inner._
       |
       |    ""$bp
       |  }
       |}
    """.stripMargin.trim()
  )
  addFileWithBreakpoints("test/JavaClass.java",
    s"""
       |package test;
       |
       |public class JavaClass {
       |
       |    public static int staticField = 0;
       |
       |    public static String staticMethod() {
       |        return "foo";
       |    }
       |
       |    public String instanceField = "bar";
       |
       |    public int instanceMethod() {
       |        return 1;
       |    }
       |
       |    public class JavaInner {
       |        public String innerField = "inner " + instanceField;
       |    }
       |}
    """.stripMargin.trim()
  )

  def testImportJava() {
    runDebugger() {
      waitForBreakpoint()
      evalEquals("staticField", "0")
      evalEquals("staticMethod", "foo")
      evalEquals("instanceField", "bar")
      evalEquals("instanceMethod", "1")
      evalEquals("innerField", "inner bar")
    }
  }

  addFileWithBreakpoints("implicits/package.scala",
    s"""
       |package object implicits {
       |  implicit def intToString(x: Int) = x.toString + x.toString
       |  implicit val implicitInt: Int = 0
       |
       |  implicit class IntWrapper(i: Int) {
       |    def triple() = 3 * i
       |  }
       |
       |  implicit class BooleanWrapper(val b: Boolean) extends AnyVal {
       |    def naoborot() = !b
       |  }
       |}
    """.stripMargin.trim)
  addFileWithBreakpoints("ImportedImplicits.scala",
    s"""
       |import implicits._
       |object ImportedImplicits {
       |  def main(args: Array[String]) {
       |    val i1 = 123
       |    def bar(s: String)(implicit i: Int) = if (i < s.length) s.charAt(i) else '0'
       |    ""$bp
       |  }
       |}
    """.stripMargin.trim)

  def testImportedImplicits() {
    runDebugger() {
      waitForBreakpoint()
      evalEquals("i1.charAt(3)", "1")
      evalEquals("\"a\".concat(i1)", "a123123")
      evalEquals("bar(\"abc\")", "a")
      evalEquals("bar(i1)", "1")
      evalEquals("2.triple()", "6")
      evalEquals("true.naoborot()", "false")
    }
  }

  addFileWithBreakpoints("ImportedFromOuterThis.scala",
   s"""
      |object ImportedFromOuterThis {
      |  def main(args: Array[String]) {
      |    val o = new OuterThis
      |    val b = new o.B()
      |    b.bar()
      |  }
      |}
      |
      |class OuterThis {
      |
      |  val g = new GGG
      |  import g._
      |
      |  class B {
      |    def bar() = {
      |      val f = foo()
      |      ""$bp
      |    }
      |  }
      |}
      |
      |class GGG {
      |  def foo() = 1
      |}
    """.stripMargin.trim)

  def testImportedFromOuterThis(): Unit = {
    runDebugger() {
      waitForBreakpoint()
      evalEquals("foo()", "1")
      evalStartsWith("g", "GGG")
      evalStartsWith("OuterThis.this", "OuterThis")
      evalStartsWith("B.this", "OuterThis$B")
      evalStartsWith("this", "OuterThis$B")
    }
  }
}
