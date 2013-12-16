package org.jetbrains.plugins.scala
package debugger.evaluateExpression

/**
 * Nikolay.Tropin
 * 12/9/13
 */
class ScalaImportedEvaluationTest extends ScalaDebuggerTestCase{
  def testImportFromObject() {
    myFixture.addFileToProject("Sample.scala",
      """
        |object Sample {
        |  def main(args: Array[String]) {
        |    import test.Stuff._
        |    "stop here"
        |  }
        |}
      """.stripMargin.trim()
    )
    myFixture.addFileToProject("Stuff.scala",
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
    myFixture.addFileToProject("Sample.scala",
      """
        |object Sample {
        |  def main(args: Array[String]) {
        |    import test.stuff._
        |    "stop here"
        |  }
        |}
      """.stripMargin.trim()
    )
    myFixture.addFileToProject("package.scala",
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

  def testImportVal() {
    myFixture.addFileToProject("Sample.scala",
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
    myFixture.addFileToProject("Sample.scala",
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
        |    val y = List(1, 2, 3)
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
    myFixture.addFileToProject("Sample.scala",
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
    myFixture.addFileToProject("test/JavaClass.java",
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
    myFixture.addFileToProject("implicits/package.scala",
      """
        |package object implicits {
        |  implicit def intToString(x: Int) = x.toString + x.toString
        |  implicit val implicitInt: Int = 0
        |}
      """.stripMargin.trim)
    myFixture.addFileToProject("Sample.scala",
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
    }
  }
}
