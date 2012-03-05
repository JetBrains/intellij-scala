package org.jetbrains.plugins.scala
package lang.scaladoc

import lang.completion3.ScalaLightPlatformCodeInsightTestCaseAdapter
import com.intellij.openapi.editor.actionSystem.EditorActionManager
import com.intellij.openapi.actionSystem.DataContext

/**
 * User: Dmitry Naydanov
 * Date: 2/25/12
 */

class WikiTagAutoCompletionTest extends ScalaLightPlatformCodeInsightTestCaseAdapter {
  private def checkGeneratedText(text: String, assumedStub: String, charTyped: Char) {
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
  }

  def testCodeLinkAC() {
    val text = "/** [<caret> */"
    val assumedStub = "/** [[]] */"
    checkGeneratedText(text, assumedStub, '[')
  }

  def testInnerCodeAC() {
    val text = "/** {{<caret> */"
    val assumedStub = "/** {{{}}} */"
    checkGeneratedText(text, assumedStub, '{')
  }

  def testMonospaceAC() {
    val text = "/** <caret> */"
    val assumedStub = "/** `` */"
    checkGeneratedText(text, assumedStub, '`')
  }

  def testSuperscriptAC() {
    val text = "/** <caret> */"
    val assumedStub = "/** ^^ */"
    checkGeneratedText(text, assumedStub, '^')
  }

  def testSubscriptAC() {
    val text = "/** ,<caret> */"
    val assumedStub = "/** ,,,, */"
    checkGeneratedText(text, assumedStub, ',')
  }

  def testBoldSimpleAC() {
    val text = "/** ''<caret>'' */"
    val assumedStub = "/** '''''' */"
    checkGeneratedText(text, assumedStub, '\'')
  }
}
