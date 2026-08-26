package org.jetbrains.plugins.scala.lang.psi

import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.extensions.{IterableOnceExt, PsiElementExt}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.ScalaVersion
import org.junit.Assert.assertEquals

class ScalaPsiPresentationUtilsTest extends ScalaLightCodeInsightFixtureTestCase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version.isScala3

  def testMethodPresentableTextWithInterleavedClauses(): Unit = {
    configureScalaFromFileText(
      """class A:
        |  def foo[T](x: T)[U](u: U): U = u
        |""".stripMargin
    )

    val function = getFile.elements.findByType[ScFunction].get
    val text = ScalaPsiPresentationUtils.methodPresentableText(function)

    assertEquals("foo[T](x: T)[U](u: U): U", text)
  }

  def testMethodPresentableTextWithMultipleInterleavedClauses(): Unit = {
    configureScalaFromFileText(
      """class A:
        |  def foo[A](a: A)[B](b: B)[C](c: C): C = c
        |""".stripMargin
    )

    val function = getFile.elements.findByType[ScFunction].get
    val text = ScalaPsiPresentationUtils.methodPresentableText(function)

    assertEquals("foo[A](a: A)[B](b: B)[C](c: C): C", text)
  }

  def testMethodPresentableTextWithInterleavedClauseAfterUsingClause(): Unit = {
    configureScalaFromFileText(
      """class A:
        |  def foo(first: Int)(using Int)[A](second: A): A = second
        |""".stripMargin
    )

    val function = getFile.elements.findByType[ScFunction].get
    val text = ScalaPsiPresentationUtils.methodPresentableText(function)

    assertEquals("foo(first: Int)(using Int)[A](second: A): A", text)
  }

  def testExtensionMethodPresentationTextUsesShortAndLongTiers(): Unit = {
    configureScalaFromFileText(
      """class User
        |
        |object Definitions:
        |  extension (target: User)
        |    def present[A](suffix: A)(using render: Render[A]): String = ???
        |
        |trait Render[A]
        |""".stripMargin
    )

    val function = getFile.elements.findByType[ScFunction].get

    assertEquals("User.present", ScalaPsiPresentationUtils.extensionMethodShortText(function))
    assertEquals("User.present[A](suffix: A)(using render: Render[A])", ScalaPsiPresentationUtils.extensionMethodPresentableText(function))
  }
}
