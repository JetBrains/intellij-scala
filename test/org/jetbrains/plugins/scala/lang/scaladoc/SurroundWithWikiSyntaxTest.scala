package org.jetbrains.plugins.scala
package lang.scaladoc

import lang.surroundWith.surrounders.scaladoc._
import util.ScalaToolsFactory
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import base.ScalaLightCodeInsightFixtureTestAdapter

/**
 * User: Dmitry Naydanov
 * Date: 3/12/12
 */

class SurroundWithWikiSyntaxTest extends ScalaLightCodeInsightFixtureTestAdapter {

  import SurroundWithWikiSyntaxTest._
  import CodeInsightTestFixture.{SELECTION_START_MARKER, SELECTION_END_MARKER}

  private def getAssumedText(text: String, tag: String) =
    text.replace(SELECTION_START_MARKER, tag).replace(SELECTION_END_MARKER, tag)

  private def checkAllSurrounders(text: String) {
    val actualText = text.stripMargin.replace("\r", "")

    for (surrounder <- surrounders) {
      checkAfterSurroundWith(actualText, getAssumedText(actualText,
        surrounder.asInstanceOf[ScalaDocWithSyntaxSurrounder].getSyntaxTag), surrounder, canSurround = true)
    }
  }

  def testSurroundSimpleData() {
    checkAllSurrounders {
      s"""
         |/**
         | * b${SELECTION_START_MARKER}lah b${SELECTION_END_MARKER}lah
         | * blah blah blah
         | */""".stripMargin.replace("\r", "")
    }
  }

  def testSurroundMultilineData() {
    checkAllSurrounders {
      s"""
         |/** blah lb${SELECTION_START_MARKER}lah akfhsdhfsadhf
         |  * skjgh dfsg shdfa hsdaf jhsad fsd
         |  * dfgas dfhgsajdf sad${SELECTION_END_MARKER}jfjsd
         |  */""".stripMargin.replace("\r", "")
    }
  }

  def testSurroundAnotherSyntax1() {
    checkAllSurrounders {
      s"""
         |/**
         | * __blah blah
         | *  dfgasdhgfjk ^ashgdfkjgds|   * ''aaaaaa''  sdkfhsadjkh^ ll
         | * sd${SELECTION_START_MARKER}hfkhsa${SELECTION_END_MARKER}dl__
         | */""".stripMargin.replace("\r", "")
    }
  }

  def testSurroundAnotherSyntax2() {
    checkAllSurrounders {
      s"""
         |/**
         | * __blah blah
         | * blkjhsd${SELECTION_START_MARKER}asdhajs ''sdfsddlk''
         | * shfg`sad`jhg${SELECTION_END_MARKER}f__
         | */""".stripMargin.replace("\r", "")
    }
  }

  def testSurroundDataWithLeadingWhitespace() {
    checkAllSurrounders {
      s"""
         |/**
         | * $SELECTION_START_MARKER      datadatad${SELECTION_END_MARKER}atadata
         | */""".stripMargin.replace("\r", "")
    }
  }

  def testSurroundWholeToken() {
    checkAllSurrounders {
      s"""
         |/**
         | * ${SELECTION_START_MARKER}comment_data$SELECTION_END_MARKER
         | */""".stripMargin.replace("\r", "")
    }
  }

  def testSurroundInTag1() {
    checkAllSurrounders {
      s"""
         |/**
         | * @param a  aaa${SELECTION_START_MARKER}aa
         | *           aaaaa${SELECTION_END_MARKER}aaa
         | */""".stripMargin.replace("\r", "")
    }
  }

  def testSurroundInTag2() {
    checkAllSurrounders {
      s"""
         |/**
         | * @todo  blah ${SELECTION_START_MARKER}blah b${SELECTION_END_MARKER}lah
         | */""".stripMargin.replace("\r", "")
    }
  }

  def testSurroundAlreadyMarkedElement1() {
    checkAllSurrounders {
      s"""
         |/**
         | * blah $SELECTION_START_MARKER^blah blah
         | * jhsdbjbhsafd^$SELECTION_END_MARKER dajsdgf
         | */""".stripMargin.replace("\r", "")
    }
  }

  def testSurroundAlreadyMarkedElement2() {
    checkAllSurrounders {
      s"""
         |/**
         | * blah ,,${SELECTION_START_MARKER}blah blha
         | * blah blah$SELECTION_END_MARKER,, blah
         | */""".stripMargin.replace("\r", "")
    }
  }

  def testCannotSurroundCrossTags() {
    val text =
      s"""
         |/**
         | * aa${SELECTION_START_MARKER}aa__sahdkljahskdhasd
         | * dajs${SELECTION_END_MARKER}kjhd__kas
         | */""".stripMargin.replace("\r", "")

    checkAfterSurroundWith(text, "", surrounders(0), canSurround = false)
  }

  def testCannotSurroundMultilineWhitespace() {
    val text =
      s"""
         |/**
         | * b${SELECTION_START_MARKER}lah blah
         | *
         | * blah blah$SELECTION_END_MARKER blah
         | */""".stripMargin.replace("\r", "")

    checkAfterSurroundWith(text, "", surrounders(0), canSurround = false)
  }

  def testCannotSurroundTagName() {
    val text =
      s"""
         |/**
         | * bla${SELECTION_START_MARKER}h blah blah
         | * @see   some${SELECTION_END_MARKER}thing
         | */""".stripMargin.replace("\r", "")

    checkAfterSurroundWith(text, "", surrounders(0), canSurround = false)
  }

  def testCannotSurroundCrossTag2() {
    val text =
      s"""
         |/**
         | * blah${SELECTION_START_MARKER}__blah${SELECTION_END_MARKER}blah__
         | */""".stripMargin.replace("\r", "")

    checkAfterSurroundWith(text, "", surrounders(0), canSurround = false)
  }

  def testCannotSurroundCrossTagWithWSAndSyntax() {
    val text =
      s"""
         |/**
         | * blah blah ${SELECTION_START_MARKER}__blah blah
         | *     blah bl${SELECTION_END_MARKER}ah blah __
         | */""".stripMargin.replace("\r", "")

    checkAfterSurroundWith(text, "", surrounders(0), canSurround = false)
  }
}

object SurroundWithWikiSyntaxTest {
  val surrounders = ScalaToolsFactory.getInstance().createSurroundDescriptors().getSurroundDescriptors()(1).getSurrounders
}