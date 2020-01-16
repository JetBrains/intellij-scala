package org.jetbrains.plugins.scala.base

import com.intellij.openapi.actionSystem.IdeActions.{ACTION_EDITOR_BACKSPACE, ACTION_EDITOR_ENTER, ACTION_EXPAND_LIVE_TEMPLATE_BY_TAB}
import com.intellij.openapi.editor.CaretState
import com.intellij.openapi.fileTypes.FileType
import org.jetbrains.plugins.scala.ScalaFileType
import org.jetbrains.plugins.scala.extensions.startCommand
import org.junit.Assert._

import scala.collection.JavaConverters

/**
  * @author adkozlov
  */
abstract class EditorActionTestBase extends ScalaLightCodeInsightFixtureTestAdapter {

  import ScalaLightCodeInsightFixtureTestAdapter.findCaretOffsets

  protected val q  : String = "\""
  protected val qq : String = "\"\""
  protected val qqq: String = "\"\"\""

  protected def fileType: FileType = ScalaFileType.INSTANCE

  protected def defaultFileName: String = s"aaa.${fileType.getDefaultExtension}"

  protected def configureByText(text: String,
                                fileName: String = defaultFileName,
                                stripTrailingSpaces: Boolean = false): Unit = {
    val (textActual, caretOffsets) = findCaretOffsets(text, stripTrailingSpaces)

    assertTrue("expected at least one caret", caretOffsets.nonEmpty)

    getFixture.configureByText(fileType, textActual)
    val editor = getFixture.getEditor
    editor.getCaretModel.moveToOffset(caretOffsets.head)
    val caretStates = caretOffsets.map { offset => new CaretState(editor.offsetToLogicalPosition(offset), null, null) }
    editor.getCaretModel.setCaretsAndSelections(JavaConverters.seqAsJavaList(caretStates))
  }

  protected def performTest(textBefore: String, textAfter: String,
                            fileName: String = defaultFileName)
                           (testBody: () => Unit): Unit = {
    val stripTrailingSpaces = false
    configureByText(textBefore, fileName, stripTrailingSpaces)

    testBody()

    val (expected, _) = findCaretOffsets(textAfter, stripTrailingSpaces)
    getFixture.checkResult(expected, stripTrailingSpaces)
  }

  protected def performTypingAction(charTyped: Char): Unit =
    getFixture.`type`(charTyped)

  protected def checkGeneratedTextAfterTyping(textBefore: String, textAfter: String, charTyped: Char,
                                              fileName: String = defaultFileName): Unit =
    performTest(textBefore, textAfter, fileName) { () =>
      performTypingAction(charTyped)
    }

  protected def performBackspaceAction(): Unit =
    performEditorAction(ACTION_EDITOR_BACKSPACE)

  protected def checkGeneratedTextAfterBackspace(textBefore: String, textAfter: String): Unit =
    performTest(textBefore, textAfter) { () =>
      performBackspaceAction()
    }

  protected def performEnterAction(): Unit =
    performEditorAction(ACTION_EDITOR_ENTER)

  protected def checkGeneratedTextAfterEnter(textBefore: String, textAfter: String): Unit =
    performTest(textBefore, textAfter) { () =>
      performEnterAction()
    }

  protected def performLiveTemplateAction(): Unit =
    performEditorAction(ACTION_EXPAND_LIVE_TEMPLATE_BY_TAB)

  protected def checkGeneratedTextAfterLiveTemplate(textBefore: String, textAfter: String): Unit =
    performTest(textBefore, textAfter) { () =>
      performLiveTemplateAction()
    }

  private def performEditorAction(action: String): Unit =
    startCommand() {
      getFixture.performEditorAction(action)
    }(getProject)
}
