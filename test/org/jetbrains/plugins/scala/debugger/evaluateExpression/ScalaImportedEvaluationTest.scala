package org.jetbrains.plugins.scala
package debugger.evaluateExpression

import org.jetbrains.plugins.scala.debugger.{ScalaDebuggerTestCase, ScalaVersion_2_11, ScalaVersion_2_12_M2}

/**
 * Nikolay.Tropin
 * 12/9/13
 */

class ScalaImportedEvaluationTest extends ScalaImportedEvaluationTestBase with ScalaVersion_2_11

class ScalaImportedEvaluationTest_2_12_M2 extends ScalaImportedEvaluationTestBase with ScalaVersion_2_12_M2

abstract class ScalaImportedEvaluationTestBase extends ScalaDebuggerTestCase{
  def testImportFromObject() {
    addFileToProject("Sample.scala",
      """
        |object Sample {
        |  def main(args: Array[String]) {
        |    import test.Stuff._
        |    "stop here"
        |  }
        |}
      """.stripMargin.trim()
    )
    addFileToProject("Stuff.scala",
      """
        |package test
        |object Stuff {
        |  val x = 0
        |  def foo() = "foo"
        |}
      """.stripMargin.trim()
    )
    addBreakpoint("Sample.scala", 3)
    runDebugger("Sample") {
      waitForBreakpoint()
      evalEquals("x", "0")
      evalEquals("foo", "foo")
      evalEquals("foo()", "foo")
    }
  }

  def testImportFromPackageObject() {
    addFileToProject("Sample.scala",
      """
        |object Sample {
        |  def main(args: Array[String]) {
        |    import test.stuff._
        |    "stop here"
        |  }
        |}
      """.stripMargin.trim()
    )
    addFileToProject("package.scala",
      """
        |package test
        |package object stuff {
        |  val x = 0
        |  def foo() = "foo"
        |}
      """.stripMargin.trim()
    )
    addBreakpoint("Sample.scala", 3)
    runDebugger("Sample") {
      waitForBreakpoint()
      evalEquals("x", "0")
      evalEquals("foo", "foo")
    }
  }

  def testStopInsidePackageObject(): Unit = {
    addFileToProject("Sample.scala",
      """
        |object Sample {
        |  def main(args: Array[String]) {
        |    import test.stuff._
        |    foo()
        |  }
        |}
      """.stripMargin.trim()
    )
    addFileToProject("package.scala",
      """
        |package test
        |package object stuff {
        |  val x = 0
        |  def foo() = "foo"
        |}
      """.stripMargin.trim()
    )
    addBreakpoint("package.scala", 3)
    runDebugger("Sample") {
      waitForBreakpoint()
      evalEquals("x", "0")
      evalEquals("foo", "foo")
    }
  }

  def testStopInsideClassInPackageObject(): Unit = {
    addFileToProject("Sample.scala",
      """
        |object Sample {
        |  def main(args: Array[String]) {
        |    import test.stuff._
        |    new AAA().bar()
        |  }
        |}
      """.stripMargin.trim()
    )
    addFileToProject("package.scala",
      """
        |package test
        |package object stuff {
        |  val x = 0
        |  def foo() = "foo"
        |
        |  class AAA {
        |    val a = "a"
        |
        |    def bar() {
        |      "stop here"
        |    }
        |  }
        |}
      """.stripMargin.trim()
    )
    addBreakpoint("package.scala", 9)
    runDebugger("Sample") {
      waitForBreakpoint()
      evalEquals("x", "0")
      evalEquals("foo", "foo")
      evalEquals("a", "a")
    }
  }

  def testStopInsideValueClass(): Unit = {
    addFileToProject("Sample.scala",
      """
        |object Sample {
        |  def main(args: Array[String]) {
        |    import test.stuff._
        |    "v".toOption
        |  }
        |}
      """.stripMargin.trim()
    )
    addFileToProject("package.scala",
      """
        |package test
        |package object stuff {
        |  val x = 0
        |
        |  implicit class ObjectExt[T](val v: T) extends AnyVal{
        |    def toOption: Option[T] = Option(v)
        |  }
        |}
      """.stripMargin.trim()
    )
    addBreakpoint("package.scala", 5)
    runDebugger("Sample") {
      waitForBreakpoint()
      evalEquals("x", "0")
      evalEquals("v", "v")
    }
  }

  def testImportVal() {
    addFileToProject("Sample.scala",
      """
        |object Sample {
        |  def main(args: Array[String]) {
        |    val a = new A(0)
        |    import a._
        |    "stop here"
        |  }
        |}
        |
        |class A(val i: Int) {
        |  val x = 0
        |  def foo() = "foo"
        |}
      """.stripMargin.trim()
    )
    addBreakpoint("Sample.scala", 4)
    runDebugger("Sample") {
      waitForBreakpoint()
      evalEquals("x", "0")
      evalEquals("i", "0")
      evalEquals("foo", "foo")
    }
  }

  def testImportProjectionType() {
    addFileToProject("Sample.scala",
      """
        |object Sample {
        |
        |  def main(args: Array[String]) {
        |    new A().foo()
        |  }
        |}
        |
        |class A {
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
        |    "stop here"
        |  }
        |
        |}
      """.stripMargin.trim()
    )
    addBreakpoint("Sample.scala", 19)
    runDebugger("Sample") {
      waitForBreakpoint()
      evalEquals("charAt(0)", "a")
      evalEquals("head", "1")
    }
  }

  def testImportJava() {
    addFileToProject("Sample.scala",
      """
        |object Sample {
        |  def main(args: Array[String]) {
        |    val jc = new test.JavaClass()
        |    import jc._
        |    import test.JavaClass._
        |
        |    val inner = new JavaInner()
        |    import inner._
        |
        |    "stop here"
        |  }
        |}
      """.stripMargin.trim()
    )
    addFileToProject("test/JavaClass.java",
      """
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
    addBreakpoint("Sample.scala", 9)
    runDebugger("Sample") {
      waitForBreakpoint()
      evalEquals("staticField", "0")
      evalEquals("staticMethod", "foo")
      evalEquals("instanceField", "bar")
      evalEquals("instanceMethod", "1")
      evalEquals("innerField", "inner bar")
    }
  }

  def testImportedImplicits() {
    addFileToProject("implicits/package.scala",
      """
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
    addFileToProject("Sample.scala",
      """
        |import implicits._
        |object Sample {
        |  def main(args: Array[String]) {
        |    val i1 = 123
        |    def bar(s: String)(implicit i: Int) = if (i < s.length) s.charAt(i) else '0'
        |    "stop"
        |  }
        |}
      """.stripMargin.trim)
    addBreakpoint("Sample.scala", 5)
    runDebugger("Sample") {
      waitForBreakpoint()
      evalEquals("i1.charAt(3)", "1")
      evalEquals("\"a\".concat(i1)", "a123123")
      evalEquals("bar(\"abc\")", "a")
      evalEquals("bar(i1)", "1")
      evalEquals("2.triple()", "6")
      evalEquals("true.naoborot()", "false")
    }
  }

  def testImportedFromOuterThis(): Unit = {
    addFileToProject("Sample.scala",
      """object Sample {
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
        |      "stop here"
        |    }
        |  }
        |}
        |
        |class GGG {
        |  def foo() = 1
        |}
      """.stripMargin.trim)
    addBreakpoint("Sample.scala", 16)
    runDebugger("Sample") {
      waitForBreakpoint()
      evalEquals("foo()", "1")
      evalStartsWith("g", "GGG")
      evalStartsWith("OuterThis.this", "OuterThis")
      evalStartsWith("B.this", "OuterThis$B")
      evalStartsWith("this", "OuterThis$B")
    }
  }
}
