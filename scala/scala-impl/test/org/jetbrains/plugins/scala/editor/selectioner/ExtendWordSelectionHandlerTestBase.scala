package org.jetbrains.plugins.scala.editor.selectioner

import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.editor.Editor
import com.intellij.testFramework.EditorTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.jetbrains.plugins.scala.extensions.StringExt

abstract class ExtendWordSelectionHandlerTestBase extends BasePlatformTestCase {

  protected val Start = EditorTestUtil.SELECTION_START_TAG
  protected val End   = EditorTestUtil.SELECTION_END_TAG
  protected val Caret = EditorTestUtil.CARET_TAG

  protected def extendSelection(editor: Editor): Unit =
    EditorTestUtil.executeAction(editor, IdeActions.ACTION_EDITOR_SELECT_WORD_AT_CARET)

  protected def doTest(editorTextStates: Seq[String]): Unit = {
    val Seq(initialState, otherStates@_*) = editorTextStates
    val fileName = "a.scala"
    myFixture.configureByText(fileName, initialState)
    otherStates.zipWithIndex.foreach { case (text, idx) =>
      extendSelection(myFixture.getEditor)
      try {
        myFixture.checkResult(text.withNormalizedSeparator)
      } catch {
        case error: java.lang.AssertionError =>
          System.err.println(s"## Test failed at step: $idx ##")
          throw error
      }
    }
  }
}
