package org.jetbrains.plugins.scala
package debugger.evaluateExpression

import org.jetbrains.plugins.scala.debugger._
import org.junit.experimental.categories.Category

@Category(Array(classOf[DebuggerTests]))
class ScalaImportedEvaluationTest_2_11 extends ScalaImportedEvaluationTestBase {
  override protected def supportedIn(version: ScalaVersion) = version == LatestScalaVersions.Scala_2_11
}

@Category(Array(classOf[DebuggerTests]))
class ScalaImportedEvaluationTest_2_12 extends ScalaImportedEvaluationTestBase {
  override protected def supportedIn(version: ScalaVersion) =
    version >= LatestScalaVersions.Scala_2_12 && version <= LatestScalaVersions.Scala_2_13
}

@Category(Array(classOf[DebuggerTests]))
class ScalaImportedEvaluationTest_3_0 extends ScalaImportedEvaluationTestBase {
  override protected def supportedIn(version: ScalaVersion) = version >= LatestScalaVersions.Scala_3_0
}

@Category(Array(classOf[DebuggerTests]))
abstract class ScalaImportedEvaluationTestBase extends ScalaDebuggerTestCase {
  addFileWithBreakpoints("ImportFromObject.scala",
    s"""
       |object ImportFromObject {
       |  def main(args: Array[String]): Unit = {
       |    import test.Stuff._
       |    println()$bp
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

  def testImportFromObject(): Unit = {
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
       |  def main(args: Array[String]): Unit = {
       |    import test.stuff._
       |    println()$bp
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
       |    def bar(): Unit = {
       |      println()$bp
       |    }
       |  }
       |
       |  implicit class ObjectExt[T](val v: T) extends AnyVal{
       |    def toOption: Option[T] = Option(v)$bp
       |  }
       |}
    """.stripMargin.trim()
  )

  def testImportFromPackageObject(): Unit = {
    runDebugger() {
      waitForBreakpoint()
      evalEquals("x", "0")
      evalEquals("foo", "foo")
    }
  }

  addFileWithBreakpoints("StopInsidePackageObject.scala",
    s"""
       |object StopInsidePackageObject {
       |  def main(args: Array[String]): Unit = {
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
       |  def main(args: Array[String]): Unit = {
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
       |  def main(args: Array[String]): Unit = {
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
       |  def main(args: Array[String]): Unit = {
       |    val a = new A(0)
       |    import a._
       |    println()$bp
       |  }
       |}
       |
       |class A(val i: Int) {
       |  val x = 0
       |  def foo() = "foo"
       |}
    """.stripMargin.trim()
  )

  def testImportVal(): Unit = {
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
       |  def main(args: Array[String]): Unit = {
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
       |  def foo(): Unit = {
       |    val b = new B
       |    import b.y._
       |    import x._
       |
       |    println()$bp
       |  }
       |
       |}
    """.stripMargin.trim()
  )

  def testImportProjectionType(): Unit = {
    runDebugger() {
      waitForBreakpoint()
      evalEquals("charAt(0)", "a")
      evalEquals("head", "1")
    }
  }

  addFileWithBreakpoints("ImportJava.scala",
    s"""
       |object ImportJava {
       |  def main(args: Array[String]): Unit = {
       |    val jc = new test.JavaClass()
       |    import jc._
       |    import test.JavaClass._
       |
       |    val inner = new JavaInner()
       |    import inner._
       |
       |    println()$bp
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

  def testImportJava(): Unit = {
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
       |  implicit def intToString(x: Int): String = x.toString + x.toString
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
       |  def main(args: Array[String]): Unit = {
       |    val i1 = 123
       |    def bar(s: String)(implicit i: Int) = if (i < s.length) s.charAt(i) else '0'
       |    println()$bp
       |  }
       |}
    """.stripMargin.trim)

  def testImportedImplicits(): Unit = {
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
      |  def main(args: Array[String]): Unit = {
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
      |      println()$bp
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
