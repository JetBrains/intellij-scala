package org.jetbrains.plugins.scala.lang.scaladoc

import com.intellij.codeInsight.generation.surroundWith.SurroundWithHandler
import com.intellij.lang.surroundWith.SurroundDescriptor
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter
import org.jetbrains.plugins.scala.extensions.{StringExt, executeWriteActionCommand}
import org.jetbrains.plugins.scala.lang.surroundWith.descriptors.ScalaSurroundDescriptors
import org.jetbrains.plugins.scala.lang.surroundWith.surrounders.scaladoc._
import org.junit.Assert.{assertFalse, assertTrue}

class SurroundWithWikiSyntaxTest extends ScalaLightCodeInsightFixtureTestAdapter {

  import SurroundWithWikiSyntaxTest._

  private def configureByText(text: String, stripTrailingSpaces: Boolean): Seq[PsiElement] = {
    val normalizedText = if (stripTrailingSpaces) text.withNormalizedSeparator.trim else text.withNormalizedSeparator
    myFixture.configureByText("dummy.scala", normalizedText)

    val selectionModel = getEditor.getSelectionModel
    descriptor
      .getElementsToSurround(getFile, selectionModel.getSelectionStart, selectionModel.getSelectionEnd)
      .toIndexedSeq
  }

  private def performTest(text: String, surrounder: ScalaDocWithSyntaxSurrounder): Unit = {
    val stripTrailingSpaces = false
    val elements = configureByText(text, stripTrailingSpaces)
    assertFalse("No elements to be surrounded", elements.isEmpty)

    executeWriteActionCommand("Surround With Test") {
      SurroundWithHandler.invoke(getProject, getEditor, getFile, surrounder)
    }(getProject)

    val expected: String = {
      val tag = surrounder.getSyntaxTag
      val normalized = text.replace(START, tag).replace(END, tag).withNormalizedSeparator
      if (stripTrailingSpaces) normalized.trim else normalized
    }
    myFixture.checkResult(expected, stripTrailingSpaces)
  }

  private def performWithAllSurrounders(text: String): Unit =
    surrounders.foreach(performTest(text, _))

  private def checkCannotBeSurrounded(text: String): Unit = {
    val elements = configureByText(text, stripTrailingSpaces = false)
    assertTrue(s"Elements to be surrounded: ${elements.mkString(", ")}", elements.isEmpty)
  }

  def testSurroundSimpleData(): Unit =
    performWithAllSurrounders(
      s"""/**
         | * b${START}lah b${END}lah
         | * blah blah blah
         | */""".stripMargin
    )

  def testSurroundMultilineData(): Unit =
    performWithAllSurrounders(
      s"""/** blah lb${START}lah akfhsdhfsadhf
         | * skjgh dfsg shdfa hsdaf jhsad fsd
         | * dfgas dfhgsajdf sad${END}jfjsd
         | */""".stripMargin
    )

  def testSurroundAnotherSyntax1(): Unit =
    performWithAllSurrounders(
      s"""/**
         | * __blah blah
         | * dfgasdhgfjk ^ashgdfkjgds|   * ''aaaaaa''  sdkfhsadjkh^ ll
         | * sd${START}hfkhsa${END}dl__
         | */""".stripMargin
    )

  def testSurroundAnotherSyntax2(): Unit =
    performWithAllSurrounders(
      s"""/**
         | * __blah blah
         | * blkjhsd${START}asdhajs ''sdfsddlk''
         | * shfg`sad`jhg${END}f__
         | */""".stripMargin
    )

  def testSurroundDataWithLeadingWhitespace(): Unit =
    performWithAllSurrounders(
      s"""/**
         | * $START      datadatad${END}atadata
         | */""".stripMargin
    )

  def testSurroundWholeToken(): Unit =
    performWithAllSurrounders(
      s"""/**
         | * ${START}comment_data$END
         | */""".stripMargin
    )

  def testSurroundInTag1(): Unit =
    performWithAllSurrounders(
      s"""/**
         | * @param a aaa${START}aa
         | *          aaaaa${END}aaa
         | */""".stripMargin
    )

  def testSurroundInTag2(): Unit =
    performWithAllSurrounders(
      s"""/**
         | * @todo blah ${START}blah b${END}lah
         | */""".stripMargin
    )

  def testSurroundAlreadyMarkedElement1(): Unit =
    performWithAllSurrounders(
      s"""/**
         | * blah $START^blah blah
         | * jhsdbjbhsafd^$END dajsdgf
         | */""".stripMargin
    )

  def testSurroundAlreadyMarkedElement2(): Unit =
    performWithAllSurrounders(
      s"""/**
         | * blah ,,${START}blah blha
         | * blah blah$END,, blah
         | */""".stripMargin
    )

  def testCannotSurroundCrossTags(): Unit =
    checkCannotBeSurrounded(
      s"""/**
         | * aa${START}aa__sahdkljahskdhasd
         | * dajs${END}kjhd__kas
         | */""".stripMargin
    )

  def testCannotSurroundMultilineWhitespace(): Unit =
    checkCannotBeSurrounded(
      s"""/**
         | * b${START}lah blah
         | *
         | * blah blah$END blah
         | */""".stripMargin
    )

  def testCannotSurroundTagName(): Unit =
    checkCannotBeSurrounded(
      s"""/**
         | * bla${START}h blah blah
         | * @see   some${END}thing
         | */""".stripMargin
    )

  def testCannotSurroundCrossTag2(): Unit =
    checkCannotBeSurrounded(
      s"""/**
         | * blah${START}__blah${END}blah__
         | */""".stripMargin
    )

  def testCannotSurroundCrossTagWithWSAndSyntax(): Unit =
    checkCannotBeSurrounded(
      s"""/**
         | * blah blah ${START}__blah blah
         | *     blah bl${END}ah blah __
         | */""".stripMargin
    )
}

object SurroundWithWikiSyntaxTest {
  private val descriptor: SurroundDescriptor = ScalaSurroundDescriptors.getSurroundDescriptors()(1)

  private val surrounders: Seq[ScalaDocWithSyntaxSurrounder] = descriptor.getSurrounders
    .collect {
      case surrounder: ScalaDocWithSyntaxSurrounder => surrounder
    }.toIndexedSeq
}