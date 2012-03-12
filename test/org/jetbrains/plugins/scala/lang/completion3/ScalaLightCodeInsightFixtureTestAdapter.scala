package org.jetbrains.plugins.scala
package lang.completion3

import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.codeInsight.folding.CodeFoldingManager
import com.intellij.testFramework.fixtures.{CodeInsightTestFixture, LightCodeInsightFixtureTestCase}
import com.intellij.codeInsight.generation.surroundWith.SurroundWithHandler
import com.intellij.lang.surroundWith.{SurroundDescriptor, Surrounder}
import util.ScalaToolsFactory

/**
 * User: Dmitry Naydanov
 * Date: 3/5/12
 */

class ScalaLightCodeInsightFixtureTestAdapter extends LightCodeInsightFixtureTestCase {
  import ScalaLightCodeInsightFixtureTestAdapter.CARET_MARKER

  protected def checkAfterSurroundWith(text: String, assumedText: String, surrounder: Surrounder, canSurround: Boolean) {
    myFixture.configureByText("dummy.scala", text)
    val scaladocSurroundDescriptor = ScalaToolsFactory.getInstance().createSurroundDescriptors().getSurroundDescriptors()(1)
    val selectionModel = myFixture.getEditor.getSelectionModel

    val elementsToSurround =
      scaladocSurroundDescriptor.getElementsToSurround(myFixture.getFile, selectionModel.getSelectionStart, selectionModel.getSelectionEnd)

    if (!canSurround) {
      assert(elementsToSurround == null || elementsToSurround.isEmpty, elementsToSurround.mkString("![", ",", "]!"))
    } else {
      assert(!elementsToSurround.isEmpty, "No elements to surround!")
      extensions.inWriteAction {
        SurroundWithHandler.invoke(myFixture.getProject, myFixture.getEditor, myFixture.getFile, surrounder)
      }
      myFixture.checkResult(assumedText)
    }
  }

  protected def checkTextHasNoErrors(text: String) {
    myFixture.configureByText("dummy.scala", text)
    CodeFoldingManager.getInstance(getProject).buildInitialFoldings(myFixture.getEditor)

    myFixture.testHighlighting(false, false, false, myFixture.getFile.getVirtualFile)
  }

  protected def performTest(text: String, assumedText: String)(testBody: () => Unit) {
    val caretIndex = text.indexOf(CARET_MARKER)
    myFixture.configureByText("dummy.scala", text.replace(CARET_MARKER, ""))
    myFixture.getEditor.getCaretModel.moveToOffset(caretIndex)

    testBody()

    myFixture.checkResult(assumedText)
  }

  /**
   * Checks file text and caret position after type action
   *
   * @param text            Initial text. Must contain CARET_MARKER substring to specify caret position
   * @param assumedText     Reference text. May not contain CARET_MARKER (in this case caret position won't be checked)
   * @param charTyped       Char typed
   */
  protected def checkGeneratedTextAfterTyping(text: String, assumedText: String, charTyped: Char) {
    performTest(text, assumedText) {
      () => myFixture.`type`(charTyped)
    }
  }

  protected def checkGeneratedTextAfterBackspace(text: String, assumedText: String) {
    performTest(text, assumedText) {
      () =>
        CommandProcessor.getInstance.executeCommand(myFixture.getProject, new Runnable {
          def run() {
            myFixture.performEditorAction(IdeActions.ACTION_EDITOR_BACKSPACE)
          }
        }, "", null)
    }
  }

  protected def checkGeneratedTextAfterEnter(text: String, assumedText: String) {
    performTest(text, assumedText) {
      () =>
        CommandProcessor.getInstance().executeCommand(myFixture.getProject, new Runnable {
          def run() {
            myFixture.performEditorAction(IdeActions.ACTION_EDITOR_ENTER)
          }
        }, "", null)
    }
  }
}

object ScalaLightCodeInsightFixtureTestAdapter {
  val CARET_MARKER = CodeInsightTestFixture.CARET_MARKER
}

