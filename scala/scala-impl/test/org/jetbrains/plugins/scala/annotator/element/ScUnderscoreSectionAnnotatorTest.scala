package org.jetbrains.plugins.scala.annotator.element

import org.jetbrains.plugins.scala.annotator.ScalaHighlightingTestBase
import org.jetbrains.plugins.scala.{ScalaBundle, ScalaVersion}

class ScUnderscoreSectionAnnotatorTest extends ScalaHighlightingTestBase {

  override protected def supportedIn(version: ScalaVersion): Boolean =
    version.isScala3

  def testFunctionTypeBeforeUnderscore(): Unit = {
    myFixture.addFileToProject("JavaClass.java",
      """public class JavaClass {
        |    public void foo1() {}
        |    public void foo2(String s) {}
        |    public String foo3() { return null; }
        |    public String foo4(String s) { return null; }
        |}
        |""".stripMargin
    )

    assertNoErrorsScala3(
      """object Usage:
        |  def foo0(): String = null
        |  def foo1(x: Int): String = null
        |
        |  val _ = foo0 _
        |  val _ = foo1 _
        |
        |  val jc = new JavaClass
        |  val _ = jc.foo1 _
        |  val _ = jc.foo2 _
        |  val _ = jc.foo3 _
        |
        |  implicit val b: Boolean = false
        |  def foo(a: Int)(implicit p: Boolean): Unit = ()
        |  foo _ : (Int => Unit)
        |  ((foo)) _ : (Int => Unit)
        |  foo : (Int => Unit)
        |
        |  def fooGeneric[T](a: T)(implicit p: Boolean): Unit = ()
        |  fooGeneric[String] _ : (String => Unit)
        |  fooGeneric[String] : (String=> Unit)
        |""".stripMargin
    )
  }

  def testNonFunctionTypeBeforeUnderscore(): Unit = {
    assertErrorsTextScala3(
      """def foo0: String = null
        |def foo00(): String = null
        |def foo1(x: Int): String = null
        |def foo2(using x: Int): String = null
        |val value: String = null
        |var variable: String = null
        |object Object
        |
        |given Int = 42
        |
        |val _ = foo0 _
        |val _ = foo00() _
        |val _ = foo1(42) _
        |val _ = foo2 _
        |val _ = value _
        |val _ = variable _
        |val _ = Object _
        |val _ = 123 _
        |""".stripMargin,
      """Error(foo0 _,Only function types can be followed by _ but the current expression has type String)
        |Error(foo00() _,Only function types can be followed by _ but the current expression has type String)
        |Error(foo1(42) _,Only function types can be followed by _ but the current expression has type String)
        |Error(foo2 _,Only function types can be followed by _ but the current expression has type String)
        |Error(value _,Only function types can be followed by _ but the current expression has type String)
        |Error(variable _,Only function types can be followed by _ but the current expression has type String)
        |Error(Object _,Only function types can be followed by _ but the current expression has type Object.type)
        |Error(123 _,Only function types can be followed by _ but the current expression has type Int)
        |""".stripMargin
    )

    applyAllQuickFixesWithText(ScalaBundle.message("rewrite.to.function.value"))

    myFixture.checkResult(
      """def foo0: String = null
        |def foo00(): String = null
        |def foo1(x: Int): String = null
        |def foo2(using x: Int): String = null
        |val value: String = null
        |var variable: String = null
        |object Object
        |
        |given Int = 42
        |
        |val _ = () => foo0
        |val _ = () => foo00()
        |val _ = () => foo1(42)
        |val _ = () => foo2
        |val _ = () => value
        |val _ = () => variable
        |val _ = () => Object
        |val _ = () => 123
        |""".stripMargin
    )
  }
}