package org.jetbrains.plugins.scala
package lang.scaladoc

import lang.completion3.ScalaLightPlatformCodeInsightTestCaseAdapter
import com.intellij.openapi.editor.actionSystem.EditorActionManager
import com.intellij.openapi.actionSystem.DataContext

/**
 * User: Dmitry Naydanov
 * Date: 2/25/12
 */

class WikiClosingTagTypedTest extends ScalaLightPlatformCodeInsightTestCaseAdapter {
   private def checkGeneratedTextAndCaretPosition(text: String, assumedStub: String, charTyped: Char, caretPosition: Int) {
     val caretIndex = text.indexOf("<caret>")

     configureFromFileTextAdapter("dummy.scala", text.replace("<caret>", ""))
     val typedHandler = EditorActionManager.getInstance().getTypedAction
     getEditorAdapter.getCaretModel.moveToOffset(caretIndex)

     typedHandler.actionPerformed(getEditorAdapter, charTyped, new DataContext {
       def getData(dataId: String): AnyRef = {
         dataId match {
           case "Language" | "language" => getFileAdapter.getLanguage
           case "Project" | "project" => getFileAdapter.getProject
           case _ => null
         }
       }
     })

     assert(getFileAdapter.getText == assumedStub)
     assert(getEditorAdapter.getCaretModel.getOffset == caretPosition)
   }

  def testCodeLinkClosingTagInput() {
    val text = "/** [[java.lang.String<caret>]] */"
    val stub1 = "/** [[java.lang.String]"
    val stub2 = "] */"

    checkGeneratedTextAndCaretPosition(text, stub1 + stub2, ']', stub1.length())
  }

  def testInnerCodeClosingTagInput() {
    val text =
    """
    |  /**
    |    *
    |    * {{{
    |    *  class A {
    |    *    def f() {}
    |    * } }}<caret>}
    |    */
    """.stripMargin.replace("\r", "")
    val stub1 =
    """
    |  /**
    |    *
    |    * {{{
    |    *  class A {
    |    *    def f() {}
    |    * } }}}""".stripMargin.replace("\r", "")
    val stub2 =
    """
    |    */
    """.stripMargin.replace("\r", "")

    checkGeneratedTextAndCaretPosition(text, stub1 + stub2, '}', stub1.length())
  }

  def testItalicClosingTagInput() {
    val text =
      """
      | /**
      |   * ''blah blah blah blah
      |   *   blah blah blah '<caret>'
      |   */
      """.stripMargin.replace("\r", "")
    val stub1 =
      """
      | /**
      |   * ''blah blah blah blah
      |   *   blah blah blah ''""".stripMargin.replace("\r", "")
    val stub2 =
      """
      |   */
      """.stripMargin.replace("\r", "")

    checkGeneratedTextAndCaretPosition(text, stub1 + stub2, '\'', stub1.length())
  }

  def testSuperscriptClosingTagInput() {
    val text = "/** 2^2<caret>^ = 4 */"
    val stub1 = "/** 2^2^"
    val stub2 = " = 4 */"

    checkGeneratedTextAndCaretPosition(text, stub1 + stub2, '^', stub1.length())
  }

  def testMonospaceClosingTag() {
    val text =
      """
      | /**
      |   * `blah-blah<caret>`
      |   */
      """.stripMargin.replace("\r", "")
    val stub1 =
      """
      | /**
      |   * `blah-blah`""".stripMargin.replace("\r", "")
    val stub2 =
      """
      |   */
      """.stripMargin.replace("\r", "")

    checkGeneratedTextAndCaretPosition(text, stub1 + stub2, '`', stub1.length())
  }

  def testBoldClosingTag() {
    val text = "/** '''blah blah blah'<caret>'' */"
    val stub1 = "/** '''blah blah blah''"
    val stub2 = "' */"

    checkGeneratedTextAndCaretPosition(text, stub1 + stub2, '\'', stub1.length())
  }

  def testUnderlinedClosingTag() {
    val text =
      """
      | /**
      |   * __blah blahblahblahblahblah
      |   *       blah blah blah blah<caret>__
      |   */
      """.stripMargin.replace("\r", "")
    val stub1 =
      """
      | /**
      |   * __blah blahblahblahblahblah
      |   *       blah blah blah blah_""".stripMargin.replace("\r", "")
    val stub2 =
      """_
      |   */
      """.stripMargin.replace("\r", "")

    checkGeneratedTextAndCaretPosition(text, stub1 + stub2, '_', stub1.length())
  }

  def testBoldTagEmpty() {
    val text = "/** '''<caret>''' */"
    val stub1 = "/** ''''"
    val stub2 = "'' */"

    checkGeneratedTextAndCaretPosition(text, stub1 + stub2, '\'', stub1.length())
  }
}
