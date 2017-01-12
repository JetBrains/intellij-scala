package org.jetbrains.plugins.scala
package lang.scaladoc

import com.intellij.testFramework.EditorTestUtil
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter
import org.jetbrains.plugins.scala.lang.surroundWith.surrounders.scaladoc._
import org.jetbrains.plugins.scala.util.ScalaToolsFactory

/**
 * User: Dmitry Naydanov
 * Date: 3/12/12
 */

class SurroundWithWikiSyntaxTest extends ScalaLightCodeInsightFixtureTestAdapter {

  import EditorTestUtil.{SELECTION_END_TAG => END, SELECTION_START_TAG => START}
  import org.jetbrains.plugins.scala.lang.scaladoc.SurroundWithWikiSyntaxTest._

  private def getAssumedText(text: String, tag: String) =
    text.replace(START, tag).replace(END, tag)

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
         |  * b${START}lah b${END}lah
         |  * blah blah blah
         |  */""".stripMargin.replace("\r", "")
    }
  }

  def testSurroundMultilineData() {
    checkAllSurrounders {
      s"""
         |/** blah lb${START}lah akfhsdhfsadhf
         |  * skjgh dfsg shdfa hsdaf jhsad fsd
         |  * dfgas dfhgsajdf sad${END}jfjsd
         |  */""".stripMargin.replace("\r", "")
    }
  }

  def testSurroundAnotherSyntax1() {
    checkAllSurrounders {
      s"""
         |/**
         |  * __blah blah
         |  * dfgasdhgfjk ^ashgdfkjgds|   * ''aaaaaa''  sdkfhsadjkh^ ll
         |  * sd${START}hfkhsa${END}dl__
         |  */""".stripMargin.replace("\r", "")
    }
  }

  def testSurroundAnotherSyntax2() {
    checkAllSurrounders {
      s"""
         |/**
         |  * __blah blah
         |  * blkjhsd${START}asdhajs ''sdfsddlk''
         |  * shfg`sad`jhg${END}f__
         |  */""".stripMargin.replace("\r", "")
    }
  }

  def testSurroundDataWithLeadingWhitespace() {
    checkAllSurrounders {
      s"""
         |/**
         |  * $START      datadatad${END}atadata
         |  */""".stripMargin.replace("\r", "")
    }
  }

  def testSurroundWholeToken() {
    checkAllSurrounders {
      s"""
         |/**
         |  * ${START}comment_data$END
         |  */""".stripMargin.replace("\r", "")
    }
  }

  def testSurroundInTag1() {
    checkAllSurrounders {
      s"""
         |/**
         |  * @param a aaa${START}aa
         |  *          aaaaa${END}aaa
         |  */""".stripMargin.replace("\r", "")
    }
  }

  def testSurroundInTag2() {
    checkAllSurrounders {
      s"""
         |/**
         |  * @todo blah ${START}blah b${END}lah
         |  */""".stripMargin.replace("\r", "")
    }
  }

  def testSurroundAlreadyMarkedElement1() {
    checkAllSurrounders {
      s"""
         |/**
         |  * blah $START^blah blah
         |  * jhsdbjbhsafd^$END dajsdgf
         |  */""".stripMargin.replace("\r", "")
    }
  }

  def testSurroundAlreadyMarkedElement2() {
    checkAllSurrounders {
      s"""
         |/**
         |  * blah ,,${START}blah blha
         |  * blah blah$END,, blah
         |  */""".stripMargin.replace("\r", "")
    }
  }

  def testCannotSurroundCrossTags() {
    val text =
      s"""
         |/**
         |  * aa${START}aa__sahdkljahskdhasd
         |  * dajs${END}kjhd__kas
         |  */""".stripMargin.replace("\r", "")

    checkAfterSurroundWith(text, "", surrounders(0), canSurround = false)
  }

  def testCannotSurroundMultilineWhitespace() {
    val text =
      s"""
         |/**
         |  * b${START}lah blah
         |  *
         |  * blah blah$END blah
         |  */""".stripMargin.replace("\r", "")

    checkAfterSurroundWith(text, "", surrounders(0), canSurround = false)
  }

  def testCannotSurroundTagName() {
    val text =
      s"""
         |/**
         |  * bla${START}h blah blah
         |  * @see   some${END}thing
         |  */""".stripMargin.replace("\r", "")

    checkAfterSurroundWith(text, "", surrounders(0), canSurround = false)
  }

  def testCannotSurroundCrossTag2() {
    val text =
      s"""
         |/**
         |  * blah${START}__blah${END}blah__
         |  */""".stripMargin.replace("\r", "")

    checkAfterSurroundWith(text, "", surrounders(0), canSurround = false)
  }

  def testCannotSurroundCrossTagWithWSAndSyntax() {
    val text =
      s"""
         |/**
         |  * blah blah ${START}__blah blah
         |  *     blah bl${END}ah blah __
         |  */""".stripMargin.replace("\r", "")

    checkAfterSurroundWith(text, "", surrounders(0), canSurround = false)
  }
}

object SurroundWithWikiSyntaxTest {
  val surrounders = ScalaToolsFactory.getInstance().createSurroundDescriptors().getSurroundDescriptors()(1).getSurrounders
}