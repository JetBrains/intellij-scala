package org.jetbrains.plugins.scala
package base

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.folding.CodeFoldingManager
import com.intellij.codeInsight.generation.surroundWith.SurroundWithHandler
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.lang.surroundWith.Surrounder
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.util.TextRange
import com.intellij.testFramework.fixtures.{CodeInsightTestFixture, LightCodeInsightFixtureTestCase}
import org.jetbrains.plugins.scala.util.ScalaToolsFactory

import scala.collection.mutable
import scala.collection.mutable.ListBuffer

/**
 * User: Dmitry Naydanov
 * Date: 3/5/12
 */

abstract class ScalaLightCodeInsightFixtureTestAdapter extends LightCodeInsightFixtureTestCase with TestFixtureProvider {

  import CodeInsightTestFixture.CARET_MARKER
  protected def normalize(str: String): String = str.stripMargin.replace("\r", "").trim

  override protected def setUp() {
    super.setUp()

    if (loadScalaLibrary) {
      fixture.allowTreeAccessForAllFiles()
      initFixture()
    }
  }

  protected override def tearDown() {
    cleanFixture()
    super.tearDown()
  }
  override final def fixture: CodeInsightTestFixture = myFixture

  override protected val rootPath: String = null

  protected def loadScalaLibrary = true

  protected def checkAfterSurroundWith(text: String, assumedText: String, surrounder: Surrounder, canSurround: Boolean) {
    fixture.configureByText("dummy.scala", text)
    val scaladocSurroundDescriptor = ScalaToolsFactory.getInstance().createSurroundDescriptors().getSurroundDescriptors()(1)
    val selectionModel = fixture.getEditor.getSelectionModel

    val elementsToSurround =
      scaladocSurroundDescriptor.getElementsToSurround(fixture.getFile, selectionModel.getSelectionStart, selectionModel.getSelectionEnd)

    if (!canSurround) {
      assert(elementsToSurround == null || elementsToSurround.isEmpty, elementsToSurround.mkString("![", ",", "]!"))
    } else {
      assert(elementsToSurround.nonEmpty, "No elements to surround!")
      extensions.startCommand(getProject, "Surround With Test") {
        SurroundWithHandler.invoke(fixture.getProject, fixture.getEditor, fixture.getFile, surrounder)
      }
      fixture.checkResult(assumedText)
    }
  }

  protected def checkTextHasNoErrors(text: String) {
    fixture.configureByText("dummy.scala", text)
    CodeFoldingManager.getInstance(getProject).buildInitialFoldings(fixture.getEditor)

    fixture.testHighlighting(false, false, false, fixture.getFile.getVirtualFile)
  }

  protected def checkTextHasNoErrors(text: String, annotation: String, inspectionsEnabled: Class[_ <: LocalInspectionTool]*) {
    import scala.collection.JavaConversions._

    fixture.configureByText("dummy.scala", text)
    fixture.enableInspections(inspectionsEnabled: _*)

    val caretIndex = text.indexOf(CARET_MARKER)
    val highlights: mutable.Buffer[HighlightInfo] = for {
      info <- fixture.doHighlighting()
      if info.getDescription == annotation
      if caretIndex == -1 || new TextRange(info.getStartOffset, info.getEndOffset).contains(caretIndex)
    } yield info
    val ranges = highlights.map(info => (info.startOffset, info.endOffset))
    assert(highlights.isEmpty, "Highlights with this errors at " + ranges.mkString("", ", ", "."))
  }

  protected def performTest(text: String, assumedText: String)(testBody: () => Unit) {
    val cleanedText =  text.replace("\r", "")
    val cleanedAssumed =  assumedText.replace("\r", "")
    val caretIndex = cleanedText.indexOf(CARET_MARKER)
    fixture.configureByText("dummy.scala", cleanedText.replace(CARET_MARKER, ""))
    fixture.getEditor.getCaretModel.moveToOffset(caretIndex)

    testBody()

    fixture.checkResult(cleanedAssumed)
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
      () => fixture.`type`(charTyped)
    }
  }

  protected def checkGeneratedTextAfterBackspace(text: String, assumedText: String) {
    performTest(text, assumedText) {
      () =>
        CommandProcessor.getInstance.executeCommand(fixture.getProject, new Runnable {
          def run() {
            fixture.performEditorAction(IdeActions.ACTION_EDITOR_BACKSPACE)
          }
        }, "", null)
    }
  }

  protected def checkGeneratedTextAfterEnter(text: String, assumedText: String) {
    performTest(text, assumedText) {
      () =>
        CommandProcessor.getInstance().executeCommand(fixture.getProject, new Runnable {
          def run() {
            fixture.performEditorAction(IdeActions.ACTION_EDITOR_ENTER)
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

    fixture.configureByText("dummy.scala", text)
    fixture.enableInspections(inspectionsEnabled: _*)
    val selectionModel = fixture.getEditor.getSelectionModel
    val selectionStart = selectionModel.getSelectionStart
    val selectionEnd = selectionModel.getSelectionEnd

    val withRightDescription = fixture.doHighlighting().filter(info => info.getDescription == annotation)
    assert(withRightDescription.nonEmpty, "No highlightings with such description: " + annotation)

    val ranges = withRightDescription.map(info => (info.getStartOffset, info.getEndOffset))
    val message = "Highlights with this description are at " + ranges.mkString(" ") + ", but has to be at " + (selectionStart, selectionEnd)
    assert(withRightDescription.exists(info => info.getStartOffset == selectionStart && info.getEndOffset == selectionEnd), message)

  }

  /**
   * Checks quick fix's result. If caret position is specified, chooses only appropriate fix.
   *
   * @param text                 File text before fix invocation
   * @param assumedStub          File text after fix invocation
   * @param quickFixHint         Fix's to perform hint
   * @param inspectionsEnabled   Enabled inspections
   */
  protected def testQuickFix(text: String, assumedStub: String, quickFixHint: String, inspectionsEnabled: Class[_ <: LocalInspectionTool]*) {
    import scala.collection.JavaConversions._

    fixture.configureByText("dummy.scala", text)
    fixture.enableInspections(inspectionsEnabled: _*)

    val actions = new ListBuffer[IntentionAction]
    val caretIndex = text.indexOf(CARET_MARKER)
    def checkCaret(startOffset: Int, endOffset: Int): Boolean = {
      if (caretIndex < 0) true
      else startOffset <= caretIndex && endOffset >= caretIndex
    }
    fixture.doHighlighting().foreach(info =>
      if (info != null && info.quickFixActionRanges != null && checkCaret(info.getStartOffset, info.getEndOffset))
        actions ++= (for (pair <- info.quickFixActionRanges if pair != null) yield pair.getFirst.getAction))

    assert(actions.nonEmpty, "There is no available fixes.")

    actions.find(_.getText == quickFixHint) match {
      case Some(action) =>
        CommandProcessor.getInstance().executeCommand(fixture.getProject, new Runnable {
          def run() {
            extensions.inWriteAction {
              action.invoke(fixture.getProject, fixture.getEditor, fixture.getFile)
            }
          }
        }, "", null)
        fixture.checkResult(assumedStub, /*stripTrailingSpaces = */true)
      case _ => assert(assertion = false, "There is no fixes with such hint.")
    }
  }
}