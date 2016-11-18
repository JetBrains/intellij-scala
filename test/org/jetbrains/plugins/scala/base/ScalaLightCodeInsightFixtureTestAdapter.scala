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
import org.jetbrains.plugins.scala.util.TestUtils.ScalaSdkVersion
import org.jetbrains.plugins.scala.util.{ScalaToolsFactory, TestUtils}

import scala.collection.mutable
import scala.collection.mutable.ListBuffer

/**
 * User: Dmitry Naydanov
 * Date: 3/5/12
 */

abstract class ScalaLightCodeInsightFixtureTestAdapter extends LightCodeInsightFixtureTestCase with TestFixtureProvider {
  protected val CARET_MARKER = ScalaLightCodeInsightFixtureTestAdapter.CARET_MARKER

  private var libLoader: ScalaLibraryLoader = _

  override protected def setUp() {
    super.setUp()

    if (loadScalaLibrary) {
      myFixture.allowTreeAccessForAllFiles()
      libLoader = ScalaLibraryLoader.withMockJdk(myFixture.getProject, myFixture.getModule, rootPath = null)
      libLoader.loadScala(libVersion)
    }
  }

  protected def libVersion: ScalaSdkVersion = TestUtils.DEFAULT_SCALA_SDK_VERSION

  protected def loadScalaLibrary = true

  protected def checkAfterSurroundWith(text: String, assumedText: String, surrounder: Surrounder, canSurround: Boolean) {
    myFixture.configureByText("dummy.scala", text)
    val scaladocSurroundDescriptor = ScalaToolsFactory.getInstance().createSurroundDescriptors().getSurroundDescriptors()(1)
    val selectionModel = myFixture.getEditor.getSelectionModel

    val elementsToSurround =
      scaladocSurroundDescriptor.getElementsToSurround(myFixture.getFile, selectionModel.getSelectionStart, selectionModel.getSelectionEnd)

    if (!canSurround) {
      assert(elementsToSurround == null || elementsToSurround.isEmpty, elementsToSurround.mkString("![", ",", "]!"))
    } else {
      assert(elementsToSurround.nonEmpty, "No elements to surround!")
      extensions.startCommand(getProject, "Surround With Test") {
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

  protected def checkTextHasNoErrors(text: String, annotation: String, inspectionsEnabled: Class[_ <: LocalInspectionTool]*) {
    import scala.collection.JavaConversions._

    myFixture.configureByText("dummy.scala", text)
    myFixture.enableInspections(inspectionsEnabled: _*)

    val caretIndex = text.indexOf(CARET_MARKER)
    val highlights: mutable.Buffer[HighlightInfo] = for {
      info <- myFixture.doHighlighting()
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
    myFixture.configureByText("dummy.scala", cleanedText.replace(CARET_MARKER, ""))
    myFixture.getEditor.getCaretModel.moveToOffset(caretIndex)

    testBody()

    myFixture.checkResult(cleanedAssumed)
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
    val selectionStart = selectionModel.getSelectionStart
    val selectionEnd = selectionModel.getSelectionEnd

    val withRightDescription = myFixture.doHighlighting().filter(info => info.getDescription == annotation)
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

    myFixture.configureByText("dummy.scala", text)
    myFixture.enableInspections(inspectionsEnabled: _*)

    val actions = new ListBuffer[IntentionAction]
    val caretIndex = text.indexOf(CARET_MARKER)
    def checkCaret(startOffset: Int, endOffset: Int): Boolean = {
      if (caretIndex < 0) true
      else startOffset <= caretIndex && endOffset >= caretIndex
    }
    myFixture.doHighlighting().foreach(info =>
      if (info != null && info.quickFixActionRanges != null && checkCaret(info.getStartOffset, info.getEndOffset))
        actions ++= (for (pair <- info.quickFixActionRanges if pair != null) yield pair.getFirst.getAction))

    assert(actions.nonEmpty, "There is no available fixes.")

    actions.find(_.getText == quickFixHint) match {
      case Some(action) =>
        CommandProcessor.getInstance().executeCommand(myFixture.getProject, new Runnable {
          def run() {
            extensions.inWriteAction {
              action.invoke(myFixture.getProject, myFixture.getEditor, myFixture.getFile)
            }
          }
        }, "", null)
        myFixture.checkResult(assumedStub, /*stripTrailingSpaces = */true)
      case _ => assert(assertion = false, "There is no fixes with such hint.")
    }
  }

  protected override def tearDown() {
    if (libLoader != null) {
      libLoader.clean()
    }
    libLoader = null
    super.tearDown()
  }

  override def getFixture: CodeInsightTestFixture = myFixture
}

object ScalaLightCodeInsightFixtureTestAdapter {
  val CARET_MARKER = CodeInsightTestFixture.CARET_MARKER
  val SELECTION_START = "<selection>"
  val SELECTION_END = "</selection>"
}

