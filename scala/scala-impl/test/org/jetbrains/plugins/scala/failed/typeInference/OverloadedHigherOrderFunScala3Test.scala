package org.jetbrains.plugins.scala.failed.typeInference

import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion, TypecheckerTests}
import org.junit.experimental.categories.Category

/**
 * In Scala 3 the alternatives of an overloaded method are narrowed by the non-function arguments,
 * before the parameter types of function literal arguments are derived from the remaining ones.
 *
 * The parameter types of a function literal are derived from the expected type of the function literal
 * computed from its context (see `ScParameter.expectedParamType`), which still considers all the alternatives.
 */
@Category(Array(classOf[TypecheckerTests]))
class OverloadedHigherOrderFunScala3Test extends ScalaLightCodeInsightFixtureTestCase {
  override protected def supportedIn(version: ScalaVersion) = version >= LatestScalaVersions.Scala_3_0

  override protected def shouldPass: Boolean = false

  def testOverloadDisambiguatedByNonFunctionArg(): Unit = checkTextHasNoErrors(
    """
      |object Test {
      |  def foo(x: Int, f: Int => Long): Int = ???
      |  def foo(x: String, f: String => Int): Double = ???
      |
      |  val result1 = foo(1, i => i * 10L)
      |  val result2 = foo("hello", s => s.length)
      |}
      |""".stripMargin
  )

  def testOverloadDisambiguatedByNonFunctionArgUnderscore(): Unit = checkTextHasNoErrors(
    """
      |object Test {
      |  def foo(x: Int, f: Int => Long): Int = ???
      |  def foo(x: String, f: String => Int): Double = ???
      |
      |  val result1 = foo(1, _ * 10L)
      |  val result2 = foo("hello", _.length)
      |}
      |""".stripMargin
  )

  def testOverloadDisambiguatedByNonFunctionArgBlock(): Unit = checkTextHasNoErrors(
    """
      |object Test {
      |  def foo(x: Int, f: Int => Long): Int = ???
      |  def foo(x: String, f: String => Int): Double = ???
      |
      |  val result1 = foo(1, { i => i * 10L })
      |  val result2 = foo("hello", { s => s.length })
      |}
      |""".stripMargin
  )

  def testOverloadDisambiguatedByNonFunctionArgPartialFunction(): Unit = checkTextHasNoErrors(
    """
      |object Test {
      |  def foo(x: Int, f: Int => Long): Int = ???
      |  def foo(x: String, f: String => Int): Double = ???
      |
      |  val result1 = foo(1, { case i => i * 10L })
      |  val result2 = foo("hello", { case s => s.length })
      |}
      |""".stripMargin
  )

  def testOverloadDisambiguatedByNonFunctionArgPartialFunctionType(): Unit = checkTextHasNoErrors(
    """
      |object Test {
      |  def foo(x: Int, f: PartialFunction[Int, Long]): Int = ???
      |  def foo(x: String, f: PartialFunction[String, Int]): Double = ???
      |
      |  val result1 = foo(1, { case i => i * 10L })
      |  val result2 = foo("hello", { case s => s.length })
      |}
      |""".stripMargin
  )
}
