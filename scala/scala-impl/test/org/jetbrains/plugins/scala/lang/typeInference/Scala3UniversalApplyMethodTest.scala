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

  def testExplicitApply(): Unit = checkTextHasNoErrors(
    """
      |class A(i: Int)
      |
      |object Test {
      | val a = A.apply(123)
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
}
