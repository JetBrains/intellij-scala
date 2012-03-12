package org.jetbrains.plugins.scala
package lang.scaladoc

import lang.completion3.ScalaLightCodeInsightFixtureTestAdapter
import lang.surroundWith.surrounders.scaladoc._
import util.ScalaToolsFactory
import com.intellij.testFramework.fixtures.CodeInsightTestFixture

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
        surrounder.asInstanceOf[ScalaDocWithSyntaxSurrounder].getSyntaxTag), surrounder, true)
    }
  }

  def testSurroundSimpleData() {
    checkAllSurrounders {
      """
      | /**
      |   * b""" + SELECTION_START_MARKER + """lah b""" + SELECTION_END_MARKER + """lah
      |   * blah blah blah
      |   */
      """
    }
  }

  def testSurroundMultilineData() {
    checkAllSurrounders {
      """
      | /** blah lb""" + SELECTION_START_MARKER + """lah akfhsdhfsadhf
      |   * skjgh dfsg shdfa hsdaf jhsad fsd
      |   * dfgas dfhgsajdf sad""" + SELECTION_END_MARKER + """jfjsd
      |   */
      """
    }
  }

  def testSurroundAnotherSyntax1() {
    checkAllSurrounders {
      """
      | /**
      |   * __blah blah
      |   *  dfgasdhgfjk ^ashgdfkjgds
      |   * ''aaaaaa''  sdkfhsadjkh^ ll
      |   * sd""" + SELECTION_START_MARKER + """hfkhsa""" + SELECTION_END_MARKER + """dl__
      |   */
      """
    }
  }

  def testSurroundAnotherSyntax2() {
    checkAllSurrounders {
      """
      | /**
      |   * __blah blah
      |   * blkjhsd""" + SELECTION_START_MARKER + """asdhajs ''sdfsddlk''
      |   * shfg`sad`jhg""" + SELECTION_END_MARKER + """f__
      |   */
      """
    }
  }

  def testSurroundDataWithLeadingWhitespace() {
    checkAllSurrounders {
      """
      | /**
      |   * """ + SELECTION_START_MARKER + """      datadatad""" + SELECTION_END_MARKER + """atadata
      |   */
      """
    }
  }

  def testSurroundWholeToken() {
    checkAllSurrounders {
      """
      | /**
      |   *         """ + SELECTION_START_MARKER + """comment_data""" + SELECTION_END_MARKER + """
      |   */
      """
    }
  }

  def testSurroundInTag1() {
    checkAllSurrounders {
      """
      | /**
      |   * @param a  aaa""" + SELECTION_START_MARKER + """aa
      |   *           aaaaa""" + SELECTION_END_MARKER + """aaa
      |   */
      """
    }
  }

  def testSurroundInTag2() {
    checkAllSurrounders {
      """
      | /**
      |   * @todo  blah """ + SELECTION_START_MARKER + """blah b""" + SELECTION_END_MARKER + """lah
      |   */
      """
    }
  }

  def testSurroundAlreadyMarkedElement1() {
    checkAllSurrounders {
      """
      | /**
      |   * blah """ + SELECTION_START_MARKER + """^blah blah
      |   * jhsdbjbhsafd^""" + SELECTION_END_MARKER + """ dajsdgf
      |   */
      """
    }
  }

  def testSurroundAlreadyMarkedElement2() {
    checkAllSurrounders {
      """
      | /**
      |   * blah ,,""" + SELECTION_START_MARKER + """blah blha
      |   * blah blah""" + SELECTION_END_MARKER + """,, blah
      |   */
      """
    }
  }

  def testCannotSurroundCrossTags() {
    val text =
      ("""
      | /**
      |   * aa""" + SELECTION_START_MARKER + """aa__sahdkljahskdhasd
      |   * dajs""" + SELECTION_END_MARKER + """kjhd__kas
      |   */
      """).stripMargin.replace("\r", "")

    checkAfterSurroundWith(text, "", surrounders(0), false)
  }

  def testCannotSurroundMultilineWhitespace() {
    val text =
      ("""
      | /**
      |   * b""" + SELECTION_START_MARKER + """lah blah
      |   *
      |   * blah blah""" + SELECTION_END_MARKER + """ blah
      |   */
      """).stripMargin.replace("\r", "")

    checkAfterSurroundWith(text, "", surrounders(0), false)
  }

  def testCannotSurroundTagName() {
    val text =
      ("""
       | /**
       |   * bla""" + SELECTION_START_MARKER +"""h blah blah
       |   * @see   some""" + SELECTION_END_MARKER + """thing
       |   */
       """).stripMargin.replace("\r", "")

    checkAfterSurroundWith(text, "", surrounders(0), false)
  }

  def testCannotSurroundCrossTag2() {
    val text =
      ("""
      | /**
      |   * blah""" + SELECTION_START_MARKER + """__blah""" + SELECTION_END_MARKER + """blah__
      |   */
      """).stripMargin.replace("\r", "")

    checkAfterSurroundWith(text, "", surrounders(0), false)
  }
}

object SurroundWithWikiSyntaxTest {
  val surrounders = ScalaToolsFactory.getInstance().createSurroundDescriptors().getSurroundDescriptors()(1).getSurrounders
}