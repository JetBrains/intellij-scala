package org.jetbrains.plugins.scala
package lang.scaladoc

import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixtureTestCase
import com.intellij.openapi.command.CommandProcessor

/**
 * User: Dmitry Naydanov
 * Date: 2/27/12
 */

class WikiPairedTagBackspaceTest extends LightPlatformCodeInsightFixtureTestCase {
  private def checkGeneratedText(text: String, assumedStub: String) {
    val caretIndex = text.indexOf("<caret>")
    myFixture.configureByText("dummy.scala", text.replace("<caret>", ""))
    myFixture.getEditor.getCaretModel.moveToOffset(caretIndex)

    CommandProcessor.getInstance.executeCommand(myFixture.getProject, new Runnable {
      def run() {
        myFixture.performEditorAction(IdeActions.ACTION_EDITOR_BACKSPACE)
      }
    }, "", null)

    myFixture.checkResult(assumedStub)
  }

  def testDeleteUnderlinedTag() {
    checkGeneratedText("/** __<caret>blah blah__ */", "/** _blah blah */")
  }

  def testDeleteMonospaceTag() {
    checkGeneratedText("/** `<caret>blahblah` */", "/** blahblah */")
  }

  def testDeleteItalicTag() {
    checkGeneratedText("/** ''<caret>blah blah'' */", "/** 'blah blah */")
  }

  def testDeleteBoldTag() {
    checkGeneratedText("/** '''<caret>blah blah''' */", "/** ''blah blah'' */")
  }

  def testDeleteSubscriptTag() {
    checkGeneratedText("/** ,,<caret>blah blah,, */", "/** ,blah blah */")
  }

  def testDeleteInnerCodeTag() {
    val text =
      """
      | /**
      |   * {{{<caret>
      |   *   class A {
      |   *     def f () {}
      |   * }
      |   *}}}
      |   */
      """.stripMargin.replace("\r", "")
    val assumedStub =
      """
      | /**
      |   * {{
      |   *   class A {
      |   *     def f () {}
      |   * }
      |   *
      |   */
      """.stripMargin.replace("\r", "")

    checkGeneratedText(text, assumedStub)
  }

  def testDeleteCodeLinkTag() {
    checkGeneratedText("/** [[<caret>java.lang.String]] */", "/** [java.lang.String */")
  }

  def testDeleteEmptyItalicTag() {
    checkGeneratedText("/** ''<caret>'' */", "/** ' */")
  }
}
