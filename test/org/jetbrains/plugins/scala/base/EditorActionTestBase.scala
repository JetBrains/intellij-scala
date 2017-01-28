package org.jetbrains.plugins.scala.base

import com.intellij.openapi.actionSystem.IdeActions.{ACTION_EDITOR_BACKSPACE, ACTION_EDITOR_ENTER}
import org.jetbrains.plugins.scala.extensions.startCommand

/**
  * @author adkozlov
  */
abstract class EditorActionTestBase extends ScalaLightCodeInsightFixtureTestAdapter {

  import ScalaLightCodeInsightFixtureTestAdapter.findCaretOffset

  protected def configureByText(text: String, stripTrailingSpaces: Boolean = false): Unit = {
    val (actual, actualOffset) = findCaretOffset(text, stripTrailingSpaces)

    getFixture.configureByText("dummy.scala", actual)
    getFixture.getEditor.getCaretModel.moveToOffset(actualOffset)
  }

  private def performTest(text: String, expectedText: String)(testBody: () => Unit): Unit = {
    val stripTrailingSpaces = false
    configureByText(text, stripTrailingSpaces)

    testBody()

    val (expected, _) = findCaretOffset(expectedText, stripTrailingSpaces)
    getFixture.checkResult(expected, stripTrailingSpaces)
  }

  protected def performTypingAction(charTyped: Char): Unit =
    getFixture.`type`(charTyped)

  protected def checkGeneratedTextAfterTyping(actual: String, expected: String, charTyped: Char): Unit =
    performTest(actual, expected) { () =>
      performTypingAction(charTyped)
    }

  protected def performBackspaceAction(): Unit =
    performEditorAction(ACTION_EDITOR_BACKSPACE)

  protected def checkGeneratedTextAfterBackspace(actual: String, expected: String): Unit =
    performTest(actual, expected) { () =>
      performBackspaceAction()
    }

  protected def performEnterAction(): Unit =
    performEditorAction(ACTION_EDITOR_ENTER)

  protected def checkGeneratedTextAfterEnter(actual: String, expected: String): Unit =
    performTest(actual, expected) { () =>
      performEnterAction()
    }

  private def performEditorAction(action: String): Unit =
    startCommand(getProject, new Runnable {
      override def run(): Unit = getFixture.performEditorAction(action)
    }, "")
}
