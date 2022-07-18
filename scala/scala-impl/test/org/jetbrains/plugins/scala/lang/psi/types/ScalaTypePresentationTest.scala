package org.jetbrains.plugins.scala
package lang.psi.types

import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter
import org.jetbrains.plugins.scala.extensions.{IterableOnceExt, PsiElementExt}
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.junit.Assert._
import org.junit.experimental.categories.Category

@Category(Array(classOf[LanguageTests]))
class ScalaTypePresentationTest extends ScalaLightCodeInsightFixtureTestAdapter {
  override protected def supportedIn(version: ScalaVersion): Boolean = version >= LatestScalaVersions.Scala_3_0

  def testPolymorphicFunction(): Unit = assertPresentationIs(
    "[X] => Any => Nothing")

  def testLambda(): Unit = assertPresentationIs(
    "[X] =>> Any")

  def testMatchSingleCase(): Unit = assertPresentationIs(
    "A match { case Int => Char }")

  def testMatchMultipleCases(): Unit = assertPresentationIs(
    "A match { case Int => Char; case Long => String }")

  def testMatchrParenthesesInner(): Unit = {
    assertPresentationIs(
      "(A match { case Int => Char }) match { case Long => String }")

    assertPresentationIs(
      "(A => Any) match { case Int => Char }")
  }

  def testMatchrParenthesesOuter(): Unit = {
    assertPresentationIs(
      "(A match { case Int => Char }) => Any")

    assertPresentationIs(
      "[X] => (A match { case Int => Char }) => Any")
  }

  private def assertPresentationIs(tpe: String): Unit = assertPresentationIs(tpe, tpe)

  private def assertPresentationIs(tpe: String, expected: String): Unit = {
    val file = ScalaPsiElementFactory.createScalaFileFromText("type T[A] = " + tpe)(getProject)
    val typeElement = file.elements.findByType[ScTypeElement].get
    val actual = typeElement.`type`().get.presentableText(TypePresentationContext(typeElement))
    assertEquals(expected, actual)
  }
}
