package org.jetbrains.plugins.scala
package lang.scaladoc

import lang.surroundWith.surrounders.scaladoc._
import util.ScalaToolsFactory
import base.ScalaLightCodeInsightFixtureTestAdapter

/**
 * User: Dmitry Naydanov
 * Date: 3/12/12
 */

class SurroundWithWikiSyntaxTest extends ScalaLightCodeInsightFixtureTestAdapter {

  import SurroundWithWikiSyntaxTest._
  val s = ScalaLightCodeInsightFixtureTestAdapter.SELECTION_START
  val e = ScalaLightCodeInsightFixtureTestAdapter.SELECTION_END

  private def getAssumedText(text: String, tag: String) =
    text.replace(s, tag).replace(e, tag)

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
         | * b${s}lah b${e}lah
         | * blah blah blah
         | */""".stripMargin.replace("\r", "")
    }
  }

  def testSurroundMultilineData() {
    checkAllSurrounders {
      s"""
         |/** blah lb${s}lah akfhsdhfsadhf
         |  * skjgh dfsg shdfa hsdaf jhsad fsd
         |  * dfgas dfhgsajdf sad${e}jfjsd
         |  */""".stripMargin.replace("\r", "")
    }
  }

  def testSurroundAnotherSyntax1() {
    checkAllSurrounders {
      s"""
         |/**
         | * __blah blah
         | *  dfgasdhgfjk ^ashgdfkjgds|   * ''aaaaaa''  sdkfhsadjkh^ ll
         | * sd${s}hfkhsa${e}dl__
         | */""".stripMargin.replace("\r", "")
    }
  }

  def testSurroundAnotherSyntax2() {
    checkAllSurrounders {
      s"""
         |/**
         | * __blah blah
         | * blkjhsd${s}asdhajs ''sdfsddlk''
         | * shfg`sad`jhg${e}f__
         | */""".stripMargin.replace("\r", "")
    }
  }

  def testSurroundDataWithLeadingWhitespace() {
    checkAllSurrounders {
      s"""
         |/**
         | * $s      datadatad${e}atadata
         | */""".stripMargin.replace("\r", "")
    }
  }

  def testSurroundWholeToken() {
    checkAllSurrounders {
      s"""
         |/**
         | * ${s}comment_data$e
         | */""".stripMargin.replace("\r", "")
    }
  }

  def testSurroundInTag1() {
    checkAllSurrounders {
      s"""
         |/**
         | * @param a  aaa${s}aa
         | *           aaaaa${e}aaa
         | */""".stripMargin.replace("\r", "")
    }
  }

  def testSurroundInTag2() {
    checkAllSurrounders {
      s"""
         |/**
         | * @todo  blah ${s}blah b${e}lah
         | */""".stripMargin.replace("\r", "")
    }
  }

  def testSurroundAlreadyMarkedElement1() {
    checkAllSurrounders {
      s"""
         |/**
         | * blah $s^blah blah
         | * jhsdbjbhsafd^$e dajsdgf
         | */""".stripMargin.replace("\r", "")
    }
  }

  def testSurroundAlreadyMarkedElement2() {
    checkAllSurrounders {
      s"""
         |/**
         | * blah ,,${s}blah blha
         | * blah blah$e,, blah
         | */""".stripMargin.replace("\r", "")
    }
  }

  def testCannotSurroundCrossTags() {
    val text =
      s"""
         |/**
         | * aa${s}aa__sahdkljahskdhasd
         | * dajs${e}kjhd__kas
         | */""".stripMargin.replace("\r", "")

    checkAfterSurroundWith(text, "", surrounders(0), canSurround = false)
  }

  def testCannotSurroundMultilineWhitespace() {
    val text =
      s"""
         |/**
         | * b${s}lah blah
         | *
         | * blah blah$e blah
         | */""".stripMargin.replace("\r", "")

    checkAfterSurroundWith(text, "", surrounders(0), canSurround = false)
  }

  def testCannotSurroundTagName() {
    val text =
      s"""
         |/**
         | * bla${s}h blah blah
         | * @see   some${e}thing
         | */""".stripMargin.replace("\r", "")

    checkAfterSurroundWith(text, "", surrounders(0), canSurround = false)
  }

  def testCannotSurroundCrossTag2() {
    val text =
      s"""
         |/**
         | * blah${s}__blah${e}blah__
         | */""".stripMargin.replace("\r", "")

    checkAfterSurroundWith(text, "", surrounders(0), canSurround = false)
  }

  def testCannotSurroundCrossTagWithWSAndSyntax() {
    val text =
      s"""
         |/**
         | * blah blah ${s}__blah blah
         | *     blah bl${e}ah blah __
         | */""".stripMargin.replace("\r", "")

    checkAfterSurroundWith(text, "", surrounders(0), canSurround = false)
  }
}

object SurroundWithWikiSyntaxTest {
  val surrounders = ScalaToolsFactory.getInstance().createSurroundDescriptors().getSurroundDescriptors()(1).getSurrounders
}