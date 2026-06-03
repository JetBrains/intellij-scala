package org.jetbrains.plugins.scala.format

import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.extensions.{IteratorExt, PsiElementExt}
import org.jetbrains.plugins.scala.lang.psi.api.base.literals.ScStringLiteral
import org.junit.Assert.fail

class WithStrippedMarginTest extends ScalaLightCodeInsightFixtureTestCase {

  override protected def supportedIn(version: ScalaVersion): Boolean = version >= ScalaVersion.Latest.Scala_2_13

  private def assertMatchesWithStrippedMargin(fileText: String): Unit = {
    configureFromFileText(fileText)
    val stringLiteral = getFile.elements.findByType[ScStringLiteral].getOrElse {
      fail(s"Can't find string literal in file: $fileText").asInstanceOf[Nothing]
    }

    stringLiteral match {
      case WithStrippedMargin(_, _) => //ok
      case _ =>
        fail(s"String literal should match WithStrippedMargin in file: $fileText")
    }
  }


  private def assertDoesNotMatchWithStrippedMargin(fileText: String): Unit = {
    configureFromFileText(fileText)
    val stringLiteral = getFile.elements.findByType[ScStringLiteral].getOrElse {
      fail(s"Can't find string literal in file: $fileText").asInstanceOf[Nothing]
    }

    stringLiteral match {
      case WithStrippedMargin(_, _) =>
        fail(s"String literal should NOT match WithStrippedMargin in file: $fileText")
      case _ => //ok
    }
  }

  def test_Matches_WithStrippedMargin(): Unit = {
    assertMatchesWithStrippedMargin(
      s"""\"\"\"test\"\"\".stripMargin""".stripMargin
    )
  }

  def test_Matches_WithStrippedMargin_PostfixCall(): Unit = {
    assertMatchesWithStrippedMargin(
      s"""\"\"\"test\"\"\" stripMargin""".stripMargin
    )
  }

  def test_Matches_WithStrippedMargin_CustomMargin(): Unit = {
    assertMatchesWithStrippedMargin(
      s"""\"\"\"test\"\"\".stripMargin('#')""".stripMargin
    )
  }

  def test_Matches_WithStrippedMargin_CustomMargin_InfixCall(): Unit = {
    assertMatchesWithStrippedMargin(
      s"""\"\"\"test\"\"\" stripMargin '#'""".stripMargin
    )
  }

  def test_Matches_WithStrippedMargin_InterpolatedString(): Unit = {
    assertMatchesWithStrippedMargin(
      s"""s\"\"\"test\"\"\".stripMargin""".stripMargin
    )
  }

  def test_Matches_WithStrippedMargin_PostfixCall_InterpolatedString(): Unit = {
    assertMatchesWithStrippedMargin(
      s"""s\"\"\"test\"\"\" stripMargin""".stripMargin
    )
  }

  def test_Matches_WithStrippedMargin_CustomMargin_InterpolatedString(): Unit = {
    assertMatchesWithStrippedMargin(
      s"""s\"\"\"test\"\"\".stripMargin('#')""".stripMargin
    )
  }

  def test_Matches_WithStrippedMargin_CustomMargin_InfixCall_InterpolatedString(): Unit = {
    assertMatchesWithStrippedMargin(
      s"""s\"\"\"test\"\"\" stripMargin '#'""".stripMargin
    )
  }

  def test_DoesNotMatch_WithStrippedMargin_1(): Unit = {
    assertDoesNotMatchWithStrippedMargin(
      s"""\"\"\"test\"\"\"""".stripMargin
    )
  }

  def test_DoesNotMatch_WithStrippedMargin_2(): Unit = {
    assertDoesNotMatchWithStrippedMargin(
      s"""\"\"\"test\"\"\".trim""".stripMargin
    )
  }

  def test_DoesNotMatch_WithStrippedMargin_StripMarginAfterOtherStringMethods(): Unit = {
    assertDoesNotMatchWithStrippedMargin(
      s"""\"\"\"test\"\"\".trim.stripMargin""".stripMargin
    )
  }
}