package org.jetbrains.plugins.scala
package lang.scaladoc

import com.intellij.codeInsight.generation.surroundWith.SurroundWithHandler
import com.intellij.lang.surroundWith.SurroundDescriptor
import com.intellij.psi.PsiElement
import com.intellij.testFramework.EditorTestUtil.{SELECTION_END_TAG => END, SELECTION_START_TAG => START}
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter
import org.jetbrains.plugins.scala.extensions.startCommand
import org.jetbrains.plugins.scala.lang.surroundWith.descriptors.ScalaSurroundDescriptors
import org.jetbrains.plugins.scala.lang.surroundWith.surrounders.scaladoc._
import org.junit.Assert.{assertFalse, assertTrue}

/**
  * User: Dmitry Naydanov
  * Date: 3/12/12
  */
class SurroundWithWikiSyntaxTest extends ScalaLightCodeInsightFixtureTestAdapter {

  import ScalaLightCodeInsightFixtureTestAdapter.normalize
  import SurroundWithWikiSyntaxTest._

  private def configureByText(text: String, stripTrailingSpaces: Boolean): Seq[PsiElement] = {
    val normalizedText = normalize(text, stripTrailingSpaces)
    getFixture.configureByText("dummy.scala", normalizedText)

    val selectionModel = getEditor.getSelectionModel
    descriptor.getElementsToSurround(getFile, selectionModel.getSelectionStart, selectionModel.getSelectionEnd)
  }

  private def performTest(text: String, surrounder: ScalaDocWithSyntaxSurrounder): Unit = {
    val stripTrailingSpaces = false
    val elements = configureByText(text, stripTrailingSpaces)
    assertFalse("No elements to be surrounded", elements.isEmpty)

    startCommand(getProject, "Surround With Test") {
      SurroundWithHandler.invoke(getProject, getEditor, getFile, surrounder)
    }
    val surrounded = surroundWith(text, surrounder)

    val expected = normalize(surrounded, stripTrailingSpaces)
    getFixture.checkResult(expected, stripTrailingSpaces)
  }

  private def performWithAllSurrounders(text: String): Unit =
    surrounders.foreach(performTest(text, _))

  private def checkCannotBeSurrounded(text: String): Unit = {
    val elements = configureByText(text, stripTrailingSpaces = false)
    assertTrue(s"Elements to be surrounded: ${elements.mkString(", ")}", elements.isEmpty)
  }

  def testSurroundSimpleData(): Unit = {
    val text =
      s"""
         |/**
         |  * b${START}lah b${END}lah
         |  * blah blah blah
         |  */""".stripMargin
    performWithAllSurrounders(text)
  }

  def testSurroundMultilineData(): Unit = {
    val text =
      s"""
         |/** blah lb${START}lah akfhsdhfsadhf
         |  * skjgh dfsg shdfa hsdaf jhsad fsd
         |  * dfgas dfhgsajdf sad${END}jfjsd
         |  */""".stripMargin
    performWithAllSurrounders(text)
  }

  def testSurroundAnotherSyntax1(): Unit = {
    val text =
      s"""
         |/**
         |  * __blah blah
         |  * dfgasdhgfjk ^ashgdfkjgds|   * ''aaaaaa''  sdkfhsadjkh^ ll
         |  * sd${START}hfkhsa${END}dl__
         |  */""".stripMargin
    performWithAllSurrounders(text)
  }

  def testSurroundAnotherSyntax2(): Unit = {
    val text =
      s"""
         |/**
         |  * __blah blah
         |  * blkjhsd${START}asdhajs ''sdfsddlk''
         |  * shfg`sad`jhg${END}f__
         |  */""".stripMargin
    performWithAllSurrounders(text)
  }

  def testSurroundDataWithLeadingWhitespace(): Unit = {
    val text =
      s"""
         |/**
         |  * $START      datadatad${END}atadata
         |  */""".stripMargin
    performWithAllSurrounders(text)
  }

  def testSurroundWholeToken(): Unit = {
    val text =
      s"""
         |/**
         |  * ${START}comment_data$END
         |  */""".stripMargin
    performWithAllSurrounders(text)
  }

  def testSurroundInTag1(): Unit = {
    val text =
      s"""
         |/**
         |  * @param a aaa${START}aa
         |  *          aaaaa${END}aaa
         |  */""".stripMargin
    performWithAllSurrounders(text)
  }

  def testSurroundInTag2(): Unit = {
    val text =
      s"""
         |/**
         |  * @todo blah ${START}blah b${END}lah
         |  */""".stripMargin
    performWithAllSurrounders(text)
  }

  def testSurroundAlreadyMarkedElement1(): Unit = {
    val text =
      s"""
         |/**
         |  * blah $START^blah blah
         |  * jhsdbjbhsafd^$END dajsdgf
         |  */""".stripMargin
    performWithAllSurrounders(text)
  }

  def testSurroundAlreadyMarkedElement2(): Unit = {
    val text =
      s"""
         |/**
         |  * blah ,,${START}blah blha
         |  * blah blah$END,, blah
         |  */""".stripMargin
    performWithAllSurrounders(text)
  }

  def testCannotSurroundCrossTags(): Unit = {
    val text =
      s"""
         |/**
         |  * aa${START}aa__sahdkljahskdhasd
         |  * dajs${END}kjhd__kas
         |  */""".stripMargin
    checkCannotBeSurrounded(text)
  }

  def testCannotSurroundMultilineWhitespace(): Unit = {
    val text =
      s"""
         |/**
         |  * b${START}lah blah
         |  *
         |  * blah blah$END blah
         |  */""".stripMargin
    checkCannotBeSurrounded(text)
  }

  def testCannotSurroundTagName(): Unit = {
    val text =
      s"""
         |/**
         |  * bla${START}h blah blah
         |  * @see   some${END}thing
         |  */""".stripMargin
    checkCannotBeSurrounded(text)
  }

  def testCannotSurroundCrossTag2(): Unit = {
    val text =
      s"""
         |/**
         |  * blah${START}__blah${END}blah__
         |  */""".stripMargin
    checkCannotBeSurrounded(text)
  }

  def testCannotSurroundCrossTagWithWSAndSyntax(): Unit = {
    val text =
      s"""
         |/**
         |  * blah blah ${START}__blah blah
         |  *     blah bl${END}ah blah __
         |  */""".stripMargin
    checkCannotBeSurrounded(text)
  }
}

object SurroundWithWikiSyntaxTest {
  private val descriptor: SurroundDescriptor = ScalaSurroundDescriptors.getSurroundDescriptors()(1)

  private val surrounders: Seq[ScalaDocWithSyntaxSurrounder] = descriptor.getSurrounders
    .collect {
      case surrounder: ScalaDocWithSyntaxSurrounder => surrounder
    }

  private def surroundWith(text: String, surrounder: ScalaDocWithSyntaxSurrounder): String = {
    val tag = surrounder.getSyntaxTag
    text.replace(START, tag).replace(END, tag)
  }
}