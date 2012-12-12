package org.jetbrains.plugins.scala
package base

import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.codeInsight.folding.CodeFoldingManager
import com.intellij.testFramework.fixtures.{CodeInsightTestFixture, LightCodeInsightFixtureTestCase}
import com.intellij.codeInsight.generation.surroundWith.SurroundWithHandler
import com.intellij.lang.surroundWith.{SurroundDescriptor, Surrounder}
import util.ScalaToolsFactory
import com.intellij.codeInspection.LocalInspectionTool
import collection.mutable.ListBuffer
import com.intellij.codeInsight.intention.IntentionAction

/**
 * User: Dmitry Naydanov
 * Date: 3/5/12
 */

abstract class ScalaLightCodeInsightFixtureTestAdapter extends LightCodeInsightFixtureTestCase {
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

  /**
   * Checks selected text has error
   *
   * @param text                File text, must contain SELECTION_START and SELECTION_END markers.
   * @param annotation          Error message
   * @param inspectionsEnabled  Enabled inspections
   */
  protected def checkTextHasError(text: String, annotation: String, inspectionsEnabled: Class[_ <: LocalInspectionTool]*) {
    import scala.collection.JavaConversions._

    myFixture.configureByText("dummy.scala", text)
    myFixture.enableInspections(inspectionsEnabled: _*)
    val selectionModel = myFixture.getEditor.getSelectionModel

    assert(myFixture.doHighlighting().exists(info => {
      info.getStartOffset == selectionModel.getSelectionStart && info.getEndOffset == selectionModel.getSelectionEnd &&
              info.description == annotation
    }))
  }

  /**
   * Checks quick fix's result
   *
   * @param text                 File text before fix invocation
   * @param assumedStub          File text after fix invocation
   * @param quickFixHint         Fix's to perform hint
   * @param inspectionsEnabled   Enabled inspections
   */
  protected def testQuickFix(text: String, assumedStub: String, quickFixHint: String, inspectionsEnabled: Class[_ <: LocalInspectionTool]*) {
    import scala.collection.JavaConversions._

    myFixture.configureByText("dummy.scala", text)
    myFixture.enableInspections(inspectionsEnabled: _*)

    val actions = new ListBuffer[IntentionAction]
    myFixture.doHighlighting().foreach(info =>
      if (info != null && info.quickFixActionRanges != null)
        actions ++= (for (pair <- info.quickFixActionRanges if pair != null) yield pair.getFirst.getAction))

    actions.find(_.getText == quickFixHint) match {
      case Some(action) =>
        CommandProcessor.getInstance().executeCommand(myFixture.getProject, new Runnable {
          def run() {
            extensions.inWriteAction {
              action.invoke(myFixture.getProject, myFixture.getEditor, myFixture.getFile)
            }
          }
        }, "", null)
        myFixture.checkResult(assumedStub)
      case _ => assert(false)
    }
  }
}

object ScalaLightCodeInsightFixtureTestAdapter {
  val CARET_MARKER = CodeInsightTestFixture.CARET_MARKER
}

