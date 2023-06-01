package org.jetbrains.plugins.scala
package lang.typeInference

import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}
import org.junit.experimental.categories.Category

@Category(Array(classOf[TypecheckerTests]))
class Scala3UniversalApplyMethodTest extends ScalaLightCodeInsightFixtureTestCase {
  override protected def supportedIn(version: ScalaVersion): Boolean =
    version >= LatestScalaVersions.Scala_3_0

  def testSimpleScalaClass(): Unit = checkTextHasNoErrors(
    """
      |class A(i: Int)
      |
      |object Test {
      |  val a: A = A(123)
      |}
      |""".stripMargin
  )

  def testSimpleScalaClassTypeMismatch(): Unit = checkTextHasNoErrors(
    s"""
       |class A(i: Int)
       |
       |object Test {
       |  val a: A = A(${withTypeMismatchError("true", expected = "Int", actual = "Boolean")})
       |}
       |""".stripMargin
  )

  def testSimpleScalaClassTooFewArguments(): Unit = checkTextHasNoErrors(
    s"""
       |class A(i: Int, s: String)
       |
       |object Test {
       |  val a: A = A${withError("()", "Unspecified value parameters: i: Int, s: String")}
       |}
       |""".stripMargin
  )

  def testSimpleScalaClassTooManyArguments(): Unit = checkTextHasNoErrors(
    s"""
       |class A
       |
       |object Test {
       |  val a: A = A${withError("(1", "Too many arguments for method A")}, true)
       |}
       |""".stripMargin
  )

  def testWithCompanionObject(): Unit = checkTextHasNoErrors(
    """
      |class A(i: Int)
      |object A {}
      |
      |object Test {
      |  val a: A = A(213)
      |}
      |""".stripMargin
  )

  def testWithCompanionObjectTypeMismatch(): Unit = checkTextHasNoErrors(
    s"""
       |class A(i: Int)
       |object A {}
       |
       |object Test {
       |  val a: A = A(${withTypeMismatchError("true", expected = "Int", actual = "Boolean")})
       |}
       |""".stripMargin
  )

  def testAlreadyHasApply(): Unit = checkHasErrorAroundCaret(
    s"""
       |class A(i: Int)
       |object A { def apply(): A = ??? }
       |
       |object Test {
       |  val a = A$CARET(123)
       |}
       |""".stripMargin
  )

  def testJavaClass(): Unit = checkTextHasNoErrors(
    """
      |object Test {
      |  val a = String("123")
      |}
      |""".stripMargin
  )

  def testJavaClassOverloaded(): Unit = checkTextHasNoErrors(
    s"""
       |object Test {
       |  val a = ${withError("String", "Cannot resolve overloaded method 'String'")}("123", 4, 5)
       |}
       |""".stripMargin
  )

  def testWithTypeParameters(): Unit = checkTextHasNoErrors(
    """
      |class Foo[A, B](a: A, b: B)
      |
      |object Test {
      |  val foo = Foo[Int, String](1213, "213")
      |  val foo2: Foo[Double, Long] = Foo(1d, 2l)
      |}
      |""".stripMargin
  )

  def testWithTypeParametersTypeMismatch(): Unit = checkTextHasNoErrors(
    s"""
       |class Foo[A, B](a: A, b: B)
       |
       |object Test {
       |  val foo = Foo[String, Int](${withTypeMismatchError("1213", expected = "String", actual = "Int")}, 'c')
       |  val foo2: Foo[Boolean, Float] = Foo(false, List.empty[String])
       |}
       |""".stripMargin
  )

  def testExplicitApply(): Unit = checkTextHasNoErrors(
    """
      |class A(i: Int)
      |
      |object Test {
      | val a = A.apply(123)
      |}
      |""".stripMargin
  )

  def testExplicitApplyTypeMismatch(): Unit = checkTextHasNoErrors(
    s"""
       |class A(i: Int)
       |
       |object Test {
       | val a = A.apply(${withTypeMismatchError("12d", expected = "Int", actual = "Double")})
       |}
       |""".stripMargin
  )

  def testJavaWithTypeParameters(): Unit = checkTextHasNoErrors(
    """
      |object Test {
      |  val col: java.util.Collection[String] = ???
      |  val opt: java.util.Set[String] = java.util.HashSet[String](col)
      |}
      |
      |""".stripMargin
  )

  def testJavaWithTypeParametersTypeMismatch(): Unit = checkTextHasNoErrors(
    s"""
       |object Test {
       |  val col: java.util.Collection[String] = ???
       |  val opt: java.util.Set[String] =
       |    java.util.HashSet[String](${withTypeMismatchError("col", expected = "Int", actual = "util.Collection[String]")}, col)
       |}
       |
       |""".stripMargin
  )

  def testAccessibility(): Unit = checkHasErrorAroundCaret(
    s"""
       |class Foo private(i: Int)
       |
       |object Test {
       |  val f = F${CARET}oo(123)
       |}
       |""".stripMargin
  )

  def testSecondaryConstructor(): Unit = checkTextHasNoErrors(
    """
      |class Foo(i: Int) {
      |  def this(s: String) = this(123)
      |}
      |
      |object Test {
      |  val foo1: Foo = Foo(123)
      |  val foo2: Foo = Foo("123")
      |}
      |""".stripMargin
  )

//  Should work, but the compiler crashes
//  def testNonQualified(): Unit = checkTextHasNoErrors(
//    """
//      |class Foo(i: Int)
//      |object Foo {
//      |  val fooInst = apply(1)
//      |}
//      |""".stripMargin
//  )

//  Compiles, but I don't think it's worth implementing
//  def thisQualified(): Unit = checkTextHasNoErrors(
//    """
//      |class Foo(i: Int)
//      |object Foo {
//      |  val fooInst = this.apply(123)
//      |}
//      |""".stripMargin
//  )

  private def withError(text: String, description: String): String =
    s"""<error descr="$description">$text</error>"""

  private def withTypeMismatchError(text: String, expected: String, actual: String): String =
    withError(text, s"Type mismatch, expected: $expected, actual: $actual")
}
