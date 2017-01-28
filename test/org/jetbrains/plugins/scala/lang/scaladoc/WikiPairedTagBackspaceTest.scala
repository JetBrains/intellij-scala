package org.jetbrains.plugins.scala
package lang.scaladoc

import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import org.jetbrains.plugins.scala.base.EditorActionTestBase

/**
 * User: Dmitry Naydanov
 * Date: 2/27/12
 */
class WikiPairedTagBackspaceTest extends EditorActionTestBase {

  import CodeInsightTestFixture.CARET_MARKER

  def testDeleteUnderlinedTag(): Unit = {
    checkGeneratedTextAfterBackspace("/** __" + CARET_MARKER + "blah blah__ */",
      "/** _blah blah */")
  }

  def testDeleteMonospaceTag(): Unit = {
    checkGeneratedTextAfterBackspace("/** `" + CARET_MARKER + "blahblah` */",
      "/** blahblah */")
  }

  def testDeleteItalicTag(): Unit = {
    checkGeneratedTextAfterBackspace("/** ''" + CARET_MARKER + "blah blah'' */",
      "/** 'blah blah */")
  }

  def testDeleteBoldTag(): Unit = {
    checkGeneratedTextAfterBackspace("/** '''" + CARET_MARKER + "blah blah''' */",
      "/** ''blah blah'' */")
  }

  def testDeleteSubscriptTag(): Unit = {
    checkGeneratedTextAfterBackspace("/** ,," + CARET_MARKER + "blah blah,, */",
      "/** ,blah blah */")
  }

  def testScl6717(): Unit = {
    checkGeneratedTextAfterBackspace(
      s"""
         | /**
         |  * a =$CARET_MARKER b
         |  */
       """.stripMargin,
      s"""
         | /**
         |  * a $CARET_MARKER b
         |  */
       """.stripMargin
    )
  }

  def testDeleteInnerCodeTag(): Unit = {
    val text =
      ("""
      | /**
      |   * {{{""" + CARET_MARKER + """
                                      |   *   class A {
                                      |   *     def f (): Unit = {}
                                      |   * }
                                      |   *}}}
                                      |   */
      """).stripMargin.replace("\r", "")
    val assumedStub =
      """
        | /**
        |   * {{
        |   *   class A {
        |   *     def f (): Unit = {}
        |   * }
        |   *
        |   */
      """.stripMargin.replace("\r", "")

    checkGeneratedTextAfterBackspace(text, assumedStub)
  }

  def testDeleteCodeLinkTag(): Unit = {
    checkGeneratedTextAfterBackspace("/** [[" + CARET_MARKER + "java.lang.String]] */",
      "/** [java.lang.String */")
  }

  def testDeleteEmptyItalicTag(): Unit = {
    checkGeneratedTextAfterBackspace("/** ''" + CARET_MARKER + "'' */",
      "/** ' */")
  }
}
