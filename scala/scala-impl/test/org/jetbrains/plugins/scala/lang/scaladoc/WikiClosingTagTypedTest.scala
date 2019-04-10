package org.jetbrains.plugins.scala
package lang.scaladoc

import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import org.jetbrains.plugins.scala.base.EditorActionTestBase

/**
 * User: Dmitry Naydanov
 * Date: 2/25/12
 */
class WikiClosingTagTypedTest extends EditorActionTestBase {

  import CodeInsightTestFixture.CARET_MARKER

  def testCodeLinkClosingTagInput(): Unit = {
    val text = s"/** [[java.lang.String$CARET_MARKER]] */"
    val assumedStub = s"/** [[java.lang.String]$CARET_MARKER] */"

    checkGeneratedTextAfterTyping(text, assumedStub, ']')
  }

  def testInnerCodeClosingTagInput(): Unit = {
    val text =
      s"""  /**
         |    *
         |    * {{{
         |    *  class A {
         |    *    def f(): Unit = {}
         |    * } }}$CARET_MARKER}
         |    */
         |""".stripMargin.replace("\r", "")

    val assumedStub =
      s"""  /**
         |    *
         |    * {{{
         |    *  class A {
         |    *    def f(): Unit = {}
         |    * } }}}$CARET_MARKER
         |    */
         |""".stripMargin.replace("\r", "")

    checkGeneratedTextAfterTyping(text, assumedStub, '}')
  }

  def testItalicClosingTagInput(): Unit = {
    val text =
      s""" /**
         |   * ''blah blah blah blah
         |   *   blah blah blah '$CARET_MARKER'
         |   */
         |""".stripMargin.replace("\r", "")

    val assumedStub =
      s""" /**
         |   * ''blah blah blah blah
         |   *   blah blah blah ''$CARET_MARKER
         |   */
         |""".stripMargin.replace("\r", "")

    checkGeneratedTextAfterTyping(text, assumedStub, '\'')
  }

  def testSuperscriptClosingTagInput(): Unit = {
    val text = s"/** 2^2$CARET_MARKER^ = 4 */"
    val assumedStub = s"/** 2^2^$CARET_MARKER = 4 */"

    checkGeneratedTextAfterTyping(text, assumedStub, '^')
  }

  def testMonospaceClosingTag(): Unit = {
    val text =
      s""" /**
         |   * `blah-blah$CARET_MARKER`
         |   */
         |""".stripMargin.replace("\r", "")

    val assumedStub =
      s""" /**
         |   * `blah-blah`$CARET_MARKER
         |   */
         |""".stripMargin.replace("\r", "")

    checkGeneratedTextAfterTyping(text, assumedStub, '`')
  }

  def testBoldClosingTag(): Unit = {
    val text = s"/** '''blah blah blah'$CARET_MARKER'' */"
    val assumedStub = s"/** '''blah blah blah''$CARET_MARKER' */"

    checkGeneratedTextAfterTyping(text, assumedStub, '\'')
  }

  def testUnderlinedClosingTag(): Unit = {
    val text =
      s""" /**
         |   * __blah blahblahblahblahblah
         |   *       blah blah blah blah${CARET_MARKER}__
         |   */
         |""".stripMargin.replace("\r", "")

    val assumedStub =
      s""" /**
         |   * __blah blahblahblahblahblah
         |   *       blah blah blah blah_${CARET_MARKER}_
         |   */
         |""".stripMargin.replace("\r", "")

    checkGeneratedTextAfterTyping(text, assumedStub, '_')
  }

  def testBoldTagEmpty(): Unit = {
    val text = s"/** '''$CARET_MARKER''' */"
    val assumedStub = s"/** ''''$CARET_MARKER'' */"

    checkGeneratedTextAfterTyping(text, assumedStub, '\'')
  }

  def testItalicTag(): Unit = {
    val before = s"/** ''some text$CARET_MARKER'' */"
    val after1 = s"/** ''some text'$CARET_MARKER' */"
    val after2 = s"/** ''some text''$CARET_MARKER */"

    checkGeneratedTextAfterTyping(before, after1, '\'')
    checkGeneratedTextAfterTyping(after1, after2, '\'')
  }

  def testAdvanceItalicToBold(): Unit = {
    val before = s"/** ''$CARET_MARKER'' */"
    val after = s"/** '''$CARET_MARKER''' */"

    checkGeneratedTextAfterTyping(before, after, '\'')
  }
}
