package org.jetbrains.plugins.hocon
package editor

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actionSystem.EditorActionManager
import com.intellij.testFramework.EditorTestUtil
import org.junit.Assert.assertNotNull

abstract class HoconEditorActionTest(actionId: String, subpath: String) extends HoconActionTest(actionId, subpath) {

  import HoconFileSetTestCase._

  override protected def executeAction(dataContext: DataContext, editor: Editor): Unit = {
    val actionHandler = EditorActionManager.getInstance.getActionHandler(actionId)
    assertNotNull(actionHandler)

    inWriteCommandAction {
      actionHandler.execute(editor, editor.getCaretModel.getCurrentCaret, dataContext)
    }
  }

  protected def resultAfterAction(editor: Editor): String = {
    val fileText = editor.getDocument.getText
    val caretOffset = editor.getCaretModel.getOffset

    if (caretOffset >= 0 && caretOffset <= fileText.length)
      fileText.substring(0, caretOffset) + EditorTestUtil.CARET_TAG + fileText.substring(caretOffset)
    else
      fileText
  }
}
