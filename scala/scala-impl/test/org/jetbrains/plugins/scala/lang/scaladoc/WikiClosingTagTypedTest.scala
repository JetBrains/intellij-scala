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
    val text = "/** [[java.lang.String" + CARET_MARKER + "]] */"
    val assumedStub = "/** [[java.lang.String]" + CARET_MARKER + "] */"

    checkGeneratedTextAfterTyping(text, assumedStub, ']')
  }

  def testInnerCodeClosingTagInput(): Unit = {
    val text =
      ("""
         |  /**
         |    *
         |    * {{{
         |    *  class A {
         |    *    def f(): Unit = {}
         |    * } }}""" + CARET_MARKER + """}
    |    */
    """).stripMargin.replace("\r", "")

    val assumedStub =
      ("""
         |  /**
         |    *
         |    * {{{
         |    *  class A {
         |    *    def f(): Unit = {}
         |    * } }}}""" + CARET_MARKER + """
    |    */
    """).stripMargin.replace("\r", "")

    checkGeneratedTextAfterTyping(text, assumedStub, '}')
  }

  def testItalicClosingTagInput(): Unit = {
    val text =
      ("""
      | /**
      |   * ''blah blah blah blah
      |   *   blah blah blah '""" + CARET_MARKER + """'
      |   */
      """).stripMargin.replace("\r", "")

    val assumedStub =
      ("""
      | /**
      |   * ''blah blah blah blah
      |   *   blah blah blah ''""" + CARET_MARKER + """
      |   */
      """).stripMargin.replace("\r", "")

    checkGeneratedTextAfterTyping(text, assumedStub, '\'')
  }

  def testSuperscriptClosingTagInput(): Unit = {
    val text = "/** 2^2" + CARET_MARKER + "^ = 4 */"
    val assumedStub = "/** 2^2^" + CARET_MARKER + " = 4 */"

    checkGeneratedTextAfterTyping(text, assumedStub, '^')
  }

  def testMonospaceClosingTag(): Unit = {
    val text =
      ("""
      | /**
      |   * `blah-blah""" + CARET_MARKER + """`
      |   */
      """).stripMargin.replace("\r", "")

    val assumedStub =
      ("""
      | /**
      |   * `blah-blah`""" + CARET_MARKER + """
      |   */
      """).stripMargin.replace("\r", "")

    checkGeneratedTextAfterTyping(text, assumedStub, '`')
  }

  def testBoldClosingTag(): Unit = {
    val text = "/** '''blah blah blah'" + CARET_MARKER + "'' */"
    val assumedStub = "/** '''blah blah blah''" + CARET_MARKER + "' */"

    checkGeneratedTextAfterTyping(text, assumedStub, '\'')
  }

  def testUnderlinedClosingTag(): Unit = {
    val text =
      ("""
      | /**
      |   * __blah blahblahblahblahblah
      |   *       blah blah blah blah""" + CARET_MARKER + """__
      |   */
      """).stripMargin.replace("\r", "")

    val assumedStub =
      ("""
      | /**
      |   * __blah blahblahblahblahblah
      |   *       blah blah blah blah_""" + CARET_MARKER + """_
      |   */
      """).stripMargin.replace("\r", "")

    checkGeneratedTextAfterTyping(text, assumedStub, '_')
  }

  def testBoldTagEmpty(): Unit = {
    val text = "/** '''" + CARET_MARKER + "''' */"
    val assumedStub = "/** ''''" + CARET_MARKER + "'' */"

    checkGeneratedTextAfterTyping(text, assumedStub, '\'')
  }
}
