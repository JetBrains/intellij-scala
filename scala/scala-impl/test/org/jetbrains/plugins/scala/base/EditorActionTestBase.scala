package org.jetbrains.plugins.scala.base

import com.intellij.openapi.actionSystem.IdeActions.{ACTION_EDITOR_BACKSPACE, ACTION_EDITOR_ENTER, ACTION_EXPAND_LIVE_TEMPLATE_BY_TAB}
import com.intellij.openapi.editor.CaretState
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageEditorUtil
import com.intellij.testFramework.fixtures.IdeaTestExecutionPolicy
import org.jetbrains.plugins.scala.ScalaFileType
import org.jetbrains.plugins.scala.editor.DocumentExt
import org.jetbrains.plugins.scala.extensions.{inWriteCommandAction, startCommand}
import org.jetbrains.plugins.scala.util.ShortCaretMarker
import org.junit.Assert._

import scala.jdk.CollectionConverters._

/**
  * @author adkozlov
  */
abstract class EditorActionTestBase extends ScalaLightCodeInsightFixtureTestAdapter with ShortCaretMarker {

  import ScalaLightCodeInsightFixtureTestAdapter.findCaretOffsets

  protected val q  : String = "\""
  protected val qq : String = "\"\""
  protected val qqq: String = "\"\"\""

  private implicit def p: Project = getProject

  override protected def sharedProjectToken: SharedTestProjectToken = SharedTestProjectToken(this.getClass)

  protected def fileType: FileType = ScalaFileType.INSTANCE

  protected def defaultFileName: String = s"aaa.${fileType.getDefaultExtension}"

  protected def configureByText(text: String,
                                fileName: String = defaultFileName,
                                trimText: Boolean = false): Unit = {
    val (textActual, caretOffsets) = findCaretOffsets(text, trimText)

    assertTrue("expected at least one caret", caretOffsets.nonEmpty)

    getFixture.getEditor match {
      case null =>
        getFixture.configureByText(fileName, textActual)
      case editor =>
        // optimization for sequential this.configureByText calls in a single test
        // getFixture.configureByText is quite resource consuming for simple sequence of typing tests
        inWriteCommandAction {
          editor.getDocument.setText(textActual)
          editor.getDocument.commit(getProject)
        }
    }
    val editor = getFixture.getEditor
    editor.getCaretModel.moveToOffset(caretOffsets.head)
    val caretStates = caretOffsets.map { offset => new CaretState(editor.offsetToLogicalPosition(offset), null, null) }
    editor.getCaretModel.setCaretsAndSelections(caretStates.asJava)
  }

  /**
   * @param textBefore                     editor text with caret markers before the action
   * @param textAfter                      editor text with caret markers after the action
   * @param stripTrailingSpacesAfterAction whether to trim trailing editor spaces after action perform
   * @param testBody                       action to perform with `textBefore`
   */
  protected def performTest(
    textBefore: String,
    textAfter: String,
    fileName: String = defaultFileName,
    trimTestDataText: Boolean = false,
    stripTrailingSpacesAfterAction: Boolean = false,
  )(testBody: () => Unit): Unit = {
    configureByText(textBefore, fileName, trimTestDataText)

    testBody()

    val (expectedText, expectedCarets) = findCaretOffsets(textAfter, trimTestDataText)

    /**
     * Copied from [[com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl.checkResult]]
     * Replaced inner `checkResult` call with `checkCaretOffsets`
     * It allows to see caret positions together with file text directly in the diff view of failed test
     * It's more convenient then operating with caret offset (as simple integer value)
     */
    Option(IdeaTestExecutionPolicy.current).foreach(_.beforeCheckResult(getFile))
    inWriteCommandAction {
      PsiDocumentManager.getInstance(getProject).commitAllDocuments()
      EditorUtil.fillVirtualSpaceUntilCaret(InjectedLanguageEditorUtil.getTopLevelEditor(getEditor))

      try checkCaretOffsets(expectedCarets, expectedText, stripTrailingSpacesAfterAction) catch {
        case cf: org.junit.ComparisonFailure =>
          // add "before" state to conveniently view failed tests
          def afterWithBeforePrefix(after: String)=
            s"""<<<Before>>>:
               |$textBefore
               |----------------------------------------------------
               |<<<After>>>:
               |$after""".stripMargin
          val cfNew = new org.junit.ComparisonFailure(
            cf.getMessage,
            afterWithBeforePrefix(cf.getExpected),
            afterWithBeforePrefix(cf.getActual)
          )
          throw cfNew
      }
    }
  }

  protected def performTypingAction(charTyped: Char): Unit =
    getFixture.`type`(charTyped)

  protected def performTypingAction(text: String): Unit =
    getFixture.`type`(text)

  protected def checkGeneratedTextAfterTyping(textBefore: String, textAfter: String, charTyped: Char,
                                              fileName: String = defaultFileName): Unit =
    performTest(textBefore, textAfter, fileName) { () =>
      performTypingAction(charTyped)
    }

  protected def checkGeneratedTextAfterTypingText(textBefore: String, textAfter: String, textTyped: String,
                                                  fileName: String = defaultFileName): Unit =
    performTest(textBefore, textAfter, fileName) { () =>
      performTypingAction(textTyped)
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
