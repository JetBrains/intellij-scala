package org.jetbrains.plugins.scala.lang.scaladoc.editor.typing

import org.jetbrains.plugins.scala.base.EditorActionTestBase

/**
 * When we are in the end of closing wiki-syntax tag we should just move the caret and not insert any text
 */
class ScalaDocWikiClosingTagTypingTest extends EditorActionTestBase {

  def testCodeLinkClosingTagInput(): Unit =
    checkGeneratedTextAfterTypingText(
      s"/** [[java.lang.String$CARET]] */",
      s"/** [[java.lang.String]]$CARET */",
      "]]"
    )

  def testInnerCodeClosingTagInput(): Unit =
    checkGeneratedTextAfterTypingText(
      s"""  /**
         |    *
         |    * {{{
         |    *  class A {
         |    *    def f(): Unit = {}
         |    * } $CARET}}}
         |    */
         |""".stripMargin,
      s"""  /**
         |    *
         |    * {{{
         |    *  class A {
         |    *    def f(): Unit = {}
         |    * } }}}$CARET
         |    */
         |""".stripMargin,
      "}}}"
    )

  def testItalicClosingTagInput(): Unit =
    checkGeneratedTextAfterTypingText(
      s""" /**
         |   * ''blah blah blah blah
         |   *   blah blah blah $CARET''
         |   */
         |""".stripMargin,
      s""" /**
         |   * ''blah blah blah blah
         |   *   blah blah blah ''$CARET
         |   */
         |""".stripMargin,
      "''"
    )

  def testSuperscriptClosingTagInput(): Unit =
    checkGeneratedTextAfterTypingText(
      s"/** 2^2$CARET^ = 4 */",
      s"/** 2^2^$CARET = 4 */",
      "^"
    )

  def testMonospaceClosingTag(): Unit =
    checkGeneratedTextAfterTypingText(
      s""" /**
         |   * `blah-blah$CARET`
         |   */
         |""".stripMargin,
      s""" /**
         |   * `blah-blah`$CARET
         |   */
         |""".stripMargin,
      "`"
    )

  def testBoldClosingTag(): Unit =
    checkGeneratedTextAfterTypingText(
      s"/** '''blah blah blah$CARET''' */",
      s"/** '''blah blah blah'''$CARET */",
      "'''"
    )

  def testBoldTagClosingTabWithEmptyContent(): Unit =
    checkGeneratedTextAfterTypingText(
      s"/** '''$CARET''' */",
      s"/** ''''''$CARET */",
      "'''"
    )

  def testUnderlinedClosingTag(): Unit =
    checkGeneratedTextAfterTypingText(
      s""" /**
         |   * __blah blahblahblahblahblah
         |   *       blah blah blah blah${CARET}__
         |   */
         |""".stripMargin,
      s""" /**
         |   * __blah blahblahblahblahblah
         |   *       blah blah blah blah__${CARET}
         |   */
         |""".stripMargin,
       "__"
    )

  def testItalicTag(): Unit = {
    val step0 = s"/** ''some text$CARET'' */"
    val step1 = s"/** ''some text'$CARET' */"
    val step2 = s"/** ''some text''$CARET */"

    checkGeneratedTextAfterTyping(step0, step1, '\'')
    checkGeneratedTextAfterTyping(step1, step2, '\'')
  }
}
