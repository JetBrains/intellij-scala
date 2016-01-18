package org.jetbrains.plugins.hocon

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actionSystem.EditorActionManager

abstract class HoconEditorActionTest(actionId: String, subpath: String) extends HoconActionTest(actionId, subpath) {
  override protected def executeAction(dataContext: DataContext, editor: Editor) = {
    val actionHandler = EditorActionManager.getInstance.getActionHandler(actionId)
    assert(actionHandler != null)

    inWriteCommandAction {
      actionHandler.execute(editor, editor.getCaretModel.getCurrentCaret, dataContext)
    }
  }

  protected def resultAfterAction(editor: Editor) =
    insertCaret(editor.getDocument.getText, editor.getCaretModel.getOffset)
}
