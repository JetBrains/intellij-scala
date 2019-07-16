package org.jetbrains.plugins.scala.console.actions

import com.intellij.openapi.actionSystem.{DataContext, IdeActions}
import com.intellij.openapi.editor.actionSystem.{EditorAction, EditorActionHandler, EditorActionManager, EditorWriteActionHandler}
import com.intellij.openapi.editor.{Caret, Editor}

class ScalaConsoleNewLineAction extends EditorAction(new Handler())

private class Handler extends EditorWriteActionHandler(true) {
  override def isEnabledForCaret(editor: Editor, caret: Caret, dataContext: DataContext): Boolean =
    getEnterHandler.isEnabled(editor, caret, dataContext)

  override def executeWriteAction(editor: Editor, caret: Caret, dataContext: DataContext): Unit =
    getEnterHandler.execute(editor, caret, dataContext)

  private def getEnterHandler: EditorActionHandler =
    EditorActionManager.getInstance.getActionHandler(IdeActions.ACTION_EDITOR_ENTER)
}