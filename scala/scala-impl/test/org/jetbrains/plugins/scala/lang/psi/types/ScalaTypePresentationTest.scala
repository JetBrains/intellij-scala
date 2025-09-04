package org.jetbrains.plugins.scala.lang.psi.types

import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.extensions.{IterableOnceExt, PsiElementExt}
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.project.ScalaFeatures
import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}
import org.junit.Assert._

abstract class ScalaTypePresentationTestBase extends ScalaLightCodeInsightFixtureTestCase {
  case class Header(value: String)
  object Header {
    implicit val emptyHeader: Header = Header("")
  }

  def assertPresentationIs(tpe: String)(implicit header: Header): Unit =
    assertPresentationIs(tpe, tpe, header = header.value)

  def assertPresentationIs(tpe: String, expected: String)(implicit header: Header): Unit = {
    assertNotEquals(tpe, expected)
    assertPresentationIs(tpe, expected, header = header.value)
  }

  def assertPresentationIs(tpe: String, expected: String, header: String): Unit = {
    val typeElement = makeTypeElement(tpe, header)
    val actual = typeElement.`type`().get.presentableText(TypePresentationContext(typeElement), Context(typeElement))
    assertEquals(expected, actual)

    if (tpe != expected) {
      assertTypeIsSame(tpe, expected, header)
    }
  }

  private def makeTypeElement(tpe: String, header: String): ScTypeElement = {
    val file =
      ScalaPsiElementFactory.createScalaFileFromText(header + "type T[A] = " + tpe, ScalaFeatures.onlyByVersion(version))(
        getProject
      )

    file.elements.collectFirst { case e: ScTypeElement if e.getTextOffset > header.length => e }.get
  }

  private def assertTypeIsSame(a: String, b: String, header: String): Unit = {
    val at = makeTypeElement(a, header).`type`().get
    val bt = makeTypeElement(b, header).`type`().get

    assertEquals(at.canonicalText, bt.canonicalText)
  }
}

class ScalaTypePresentationTest_Scala2 extends ScalaTypePresentationTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version.isScala2


  // Note: In Scala, precedence of operators is not taken into account for infix types
  def testInfixTypes(): Unit = {
    implicit val header: Header = Header(
      """
        |class +[A, B]
        |class *[A, B]
        |class *:[A, B]
        |""".stripMargin
    )
    assertPresentationIs("Int + Int")
    assertPresentationIs("Int + Int + Int")
    assertPresentationIs("(Int + Int) + Int", "Int + Int + Int")
    assertPresentationIs("Int + (Int + Int)")
    assertPresentationIs("Int * Int + Int")
    assertPresentationIs("Int + Int * Int")
    assertPresentationIs("Int + (Int * Int)")
    assertPresentationIs("(Int * Int) + Int", "Int * Int + Int")
    assertPresentationIs("Int * (Int + Int)")
    assertPresentationIs("(Int + Int) * Int", "Int + Int * Int")

    assertPresentationIs("Int *: Int *: Int")
    assertPresentationIs("(Int *: Int) *: Int")
    assertPresentationIs("Int *: (Int *: Int)", "Int *: Int *: Int")

    assertPresentationIs("(Int *: Int) + Int")
    assertPresentationIs("Int *: (Int + Int)")
    assertPresentationIs("(Int + Int) *: Int")
    assertPresentationIs("Int + (Int *: Int)")

    assertPresentationIs("(Int *: Int) * Int")
    assertPresentationIs("Int *: (Int * Int)")
    assertPresentationIs("(Int * Int) *: Int")
    assertPresentationIs("Int * (Int *: Int)")
  }
}

class ScalaTypePresentationTest_Scala3 extends ScalaTypePresentationTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version.isScala3

  override protected def additionalCompilerOptions = Seq("-Ykind-projector")

  def testPolymorphicFunction(): Unit = assertPresentationIs(
    "[X] => Any => Nothing")

  def testLambda(): Unit = assertPresentationIs(
    "[X] =>> Any")

  def testMatchSingleCase(): Unit = assertPresentationIs(
    "A match { case Int => Char }")

  def testMatchMultipleCases(): Unit = assertPresentationIs(
    "A match { case Int => Char; case Long => String }")

  def testMatchParenthesesInner(): Unit = {
    assertPresentationIs(
      "(A match { case Int => Char }) match { case Long => String }")

    assertPresentationIs(
      "(A => Any) match { case Int => Char }")
  }

  def testMatchParenthesesOuter(): Unit = {
    assertPresentationIs(
      "(A match { case Int => Char }) => Any")

    assertPresentationIs(
      "[X] => (A match { case Int => Char }) => Any")
  }

  def testKindProjector(): Unit = assertPresentationIs(
    header =   "class HCT[A[X]]; class TC2[A, B]; ",
    tpe =      "HCT[TC2[Int, *]]",
    expected = "HCT[TC2[Int, *]]"
  )

  def testKindProjectorTuple(): Unit = assertPresentationIs(
    header =   "class HCT[A[X]]; class TC2[A, B]; ",
    tpe =      "HCT[(Int, *)]",
    expected = "HCT[(Int, *)]"
  )

  def testKindProjectorFunction(): Unit = assertPresentationIs(
    header =   "class HCT[A[X]]; class TC2[A, B]; ",
    tpe =      "HCT[Int => *]",
    expected = "HCT[Int => *]"
  )

  def testOpaqueTypeInt(): Unit = {
    assertPresentationIs(
      "Inside.T", "Inside.T", "object Inside { opaque type T = Int }")
  }

  def testOpaqueTypeTuple(): Unit = {
    assertPresentationIs(
      "Inside.T", "Inside.T", "object Inside { opaque type T = (Int, Int) }")
  }

  def testInfixTypes(): Unit = {
    implicit val header: Header = Header(
      """
        |class +[A, B]
        |class *[A, B]
        |class *:[A, B]
        |""".stripMargin
    )
    assertPresentationIs("Int + Int")
    assertPresentationIs("Int + Int + Int")
    assertPresentationIs("(Int + Int) + Int", "Int + Int + Int")
    assertPresentationIs("Int + (Int + Int)")
    assertPresentationIs("Int * Int + Int")
    assertPresentationIs("Int + Int * Int")

    assertPresentationIs("Int + (Int * Int)", "Int + Int * Int")
    assertPresentationIs("(Int * Int) + Int", "Int * Int + Int")

    assertPresentationIs("Int * (Int + Int)")
    assertPresentationIs("(Int + Int) * Int")

    assertPresentationIs("Int & Boolean")
    assertPresentationIs("Boolean & Int")

    assertPresentationIs("Int & Boolean | Float")
    assertPresentationIs("Int | Boolean & Float")

    assertPresentationIs("(Int & Boolean) | Float", "Int & Boolean | Float")
    assertPresentationIs("Int | (Boolean & Float)", "Int | Boolean & Float")
    assertPresentationIs("Int & (Boolean | Float)")
    assertPresentationIs("(Int | Boolean) & Float")

    assertPresentationIs("Int *: Int *: Int")
    assertPresentationIs("(Int *: Int) *: Int")
    assertPresentationIs("Int *: (Int *: Int)", "Int *: Int *: Int")

    assertPresentationIs("(Int *: Int) + Int", "Int *: Int + Int")
    assertPresentationIs("Int *: (Int + Int)")
    assertPresentationIs("(Int + Int) *: Int")
    assertPresentationIs("Int + (Int *: Int)", "Int + Int *: Int")

    assertPresentationIs("(Int *: Int) * Int")
    assertPresentationIs("Int *: (Int * Int)")
    assertPresentationIs("(Int * Int) *: Int")
    assertPresentationIs("Int * (Int *: Int)")
  }
}
