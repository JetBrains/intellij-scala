package org.jetbrains.plugins.scala
package debugger
package evaluation

class ImportedEvaluationTest_2_11 extends ImportedEvaluationTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_2_11
}

class ImportedEvaluationTest_2_12 extends ImportedEvaluationTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_2_12
}

class ImportedEvaluationTest_2_13 extends ImportedEvaluationTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_2_13
}

class ImportedEvaluationTest_3 extends ImportedEvaluationTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_3
}

class ImportedEvaluationTest_3_RC extends ImportedEvaluationTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_3_RC
}

abstract class ImportedEvaluationTestBase extends ExpressionEvaluationTestBase {
  addSourceFile("ImportFromObject.scala",
    s"""
       |object ImportFromObject {
       |  def main(args: Array[String]): Unit = {
       |    import test.OtherStuff._
       |    println() $breakpoint
       |  }
       |}
    """.stripMargin.trim
  )
  addSourceFile("test/OtherStuff.scala",
    s"""package test
       |object OtherStuff {
       |  val x = 0
       |  def foo() = "foo"
       |}
    """.stripMargin.trim
  )

  def testImportFromObject(): Unit = {
    expressionEvaluationTest() { implicit ctx =>
      evalEquals("x", "0")
      evalEquals("foo", "foo")
      evalEquals("foo()", "foo")
    }
  }

  addSourceFile("ImportFromPackageObject.scala",
    s"""object ImportFromPackageObject {
       |  def main(args: Array[String]): Unit = {
       |    import test.stuff._
       |    println() $breakpoint
       |  }
       |}
    """.stripMargin.trim
  )
  addSourceFile("test/stuff/package.scala",
    s"""
       |package test
       |package object stuff {
       |  val x = 0
       |  def foo() = "foo"
       |
       |  class AAA {
       |    val a = "a"
       |
       |    def bar(): Unit = {
       |      println()
       |    }
       |  }
       |
       |  implicit class ObjectExt[T](val v: T) extends AnyVal{
       |    def toOption: Option[T] = Option(v)
       |  }
       |}
    """.stripMargin.trim
  )

  def testImportFromPackageObject(): Unit = {
    expressionEvaluationTest() { implicit ctx =>
      evalEquals("x", "0")
      evalEquals("foo", "foo")
    }
  }

  addSourceFile("StopInsideObject.scala",
    s"""object ObjectToStopIn1 {
       |  val x = 0
       |  def foo = "foo" $breakpoint
       |}
       |
       |object StopInsideObject {
       |  def main(args: Array[String]): Unit = {
       |    import ObjectToStopIn1._
       |    foo
       |  }
       |}
    """.stripMargin.trim
  )

  def testStopInsideObject(): Unit = {
    expressionEvaluationTest() { implicit ctx =>
      evalEquals("x", "0")
      evalEquals("foo", "foo")
    }
  }

  addSourceFile("StopInsideClassInObject.scala",
    s"""object ObjectToStopIn2 {
       |  val x = 0
       |  def foo = "foo"
       |
       |  class AAA {
       |    val a = "a"
       |
       |    def bar(): Unit = {
       |      println() $breakpoint
       |    }
       |  }
       |}
       |
       |object StopInsideClassInObject {
       |  def main(args: Array[String]): Unit = {
       |    import ObjectToStopIn2._
       |    new AAA().bar()
       |  }
       |}
    """.stripMargin.trim
  )

  def testStopInsideClassInObject(): Unit = {
    expressionEvaluationTest() { implicit ctx =>
      evalEquals("x", "0")
      evalEquals("foo", "foo")
      evalEquals("a", "a")
    }
  }

  addSourceFile("StopInsideValueClass.scala",
    s"""object ObjectToStopIn3 {
       |  val x = 0
       |  def foo() = "foo"
       |
       |  class AAA {
       |    val a = "a"
       |
       |    def bar(): Unit = {
       |      println()
       |    }
       |  }
       |
       |  implicit class ObjectExt[T](val v: T) extends AnyVal{
       |    def toOption: Option[T] = Option(v) $breakpoint
       |  }
       |}
       |
       |object StopInsideValueClass {
       |  def main(args: Array[String]): Unit = {
       |    import ObjectToStopIn3._
       |    "v".toOption
       |  }
       |}
    """.stripMargin.trim
  )

  def testStopInsideValueClass(): Unit = {
    expressionEvaluationTest() { implicit ctx =>
      evalEquals("x", "0")
      evalEquals("v", "v")
    }
  }

  addSourceFile("ImportVal.scala",
    s"""
       |object ImportVal {
       |  def main(args: Array[String]): Unit = {
       |    val a = new A(0)
       |    import a._
       |    println() $breakpoint
       |  }
       |}
       |
       |class A(val i: Int) {
       |  val x = 0
       |  def foo() = "foo"
       |}
    """.stripMargin.trim
  )

  def testImportVal(): Unit = {
    expressionEvaluationTest() { implicit ctx =>
      evalEquals("x", "0")
      evalEquals("i", "0")
      evalEquals("foo", "foo")
    }
  }

  addSourceFile("ImportProjectionType.scala",
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
       |    println() $breakpoint
       |  }
       |
       |}
    """.stripMargin.trim
  )

  def testImportProjectionType(): Unit = {
    expressionEvaluationTest() { implicit ctx =>
      evalEquals("charAt(0)", "a")
      evalEquals("head", "1")
    }
  }

  addSourceFile("ImportJava.scala",
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
       |    println() $breakpoint
       |  }
       |}
    """.stripMargin.trim
  )
  addSourceFile("test/JavaClass.java",
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
    """.stripMargin.trim
  )

  def testImportJava(): Unit = {
    expressionEvaluationTest() { implicit ctx =>
      evalEquals("staticField", "0")
      evalEquals("staticMethod", "foo")
      evalEquals("instanceField", "bar")
      evalEquals("instanceMethod", "1")
      evalEquals("innerField", "inner bar")
    }
  }

  addSourceFile("implicits/package.scala",
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
  addSourceFile("ImportedImplicits.scala",
    s"""
       |import implicits._
       |object ImportedImplicits {
       |  def main(args: Array[String]): Unit = {
       |    val i1 = 123
       |    def bar(s: String)(implicit i: Int) = if (i < s.length) s.charAt(i) else '0'
       |    println() $breakpoint
       |  }
       |}
    """.stripMargin.trim)

  def testImportedImplicits(): Unit = {
    expressionEvaluationTest() { implicit ctx =>
      evalEquals("i1.charAt(3)", "1")
      evalEquals("\"a\".concat(i1)", "a123123")
      evalEquals("bar(\"abc\")", "a")
      evalEquals("bar(i1)", "1")
      evalEquals("2.triple()", "6")
      evalEquals("true.naoborot()", "false")
    }
  }

  addSourceFile("ImportedFromOuterThis.scala",
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
       |      println() $breakpoint
       |    }
       |  }
       |}
       |
       |class GGG {
       |  def foo() = 1
       |}
    """.stripMargin.trim)

  def testImportedFromOuterThis(): Unit = {
    expressionEvaluationTest() { implicit ctx =>
      evalEquals("foo()", "1")
      evalStartsWith("g", "GGG")
      evalStartsWith("OuterThis.this", "OuterThis")
      evalStartsWith("B.this", "OuterThis$B")
      evalStartsWith("this", "OuterThis$B")
    }
  }
}
