package org.jetbrains.plugins.scala.failed.typeInference

import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion, TypecheckerTests}
import org.junit.experimental.categories.Category

/**
 * The type of the parameter of a function literal depends on a type parameter, which is only determined
 * by the implicit arguments of a preceding using clause.
 *
 * The parameter types of a function literal are derived from the expected type of the function literal
 * computed from its context (see `ScParameter.expectedParamType`), where the implicit arguments are not applied yet.
 */
@Category(Array(classOf[TypecheckerTests]))
class MultipleAndInterleavedUsingClausesTest extends ScalaLightCodeInsightFixtureTestCase {
  override protected def supportedIn(version: ScalaVersion): Boolean =
    version >= LatestScalaVersions.Scala_3_7

  override protected def shouldPass: Boolean = false

  def testUsingDependentFunctionLiteralParam(): Unit = checkTextHasNoErrors(
    """
      |object A {
      |  trait Foo[A]
      |  given Foo[String] = ???
      |  def foo[A](using Foo[A])(fn: A => Int): Int = ???
      |  foo(x => x.toInt)
      |}
      |""".stripMargin
  )

  def testUsingDependentUnderscoreSection(): Unit = checkTextHasNoErrors(
    """
      |object A {
      |  trait Foo[A]
      |  given Foo[String] = ???
      |  def foo[A](using Foo[A])(fn: A => Int): Int = ???
      |  foo(_.toInt)
      |}
      |""".stripMargin
  )

  def testUsingDependentBlockFunctionLiteral(): Unit = checkTextHasNoErrors(
    """
      |object A {
      |  trait Foo[A]
      |  given Foo[String] = ???
      |  def foo[A](using Foo[A])(fn: A => Int): Int = ???
      |  foo { x => x.toInt }
      |}
      |""".stripMargin
  )

  def testUsingDependentPartialFunction(): Unit = checkTextHasNoErrors(
    """
      |object A {
      |  trait Foo[A]
      |  given Foo[String] = ???
      |  def foo[A](using Foo[A])(fn: A => Int): Int = ???
      |  foo { case x => x.toInt }
      |}
      |""".stripMargin
  )
}
