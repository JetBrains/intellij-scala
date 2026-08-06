package org.jetbrains.plugins.scala.console.actions

import com.intellij.openapi.actionSystem.{DataContext, IdeActions}
import com.intellij.openapi.editor.actionSystem.{EditorAction, EditorActionHandler, EditorActionManager, EditorWriteActionHandler}
import com.intellij.openapi.editor.{Caret, Editor}
import org.jetbrains.plugins.scala.console.{ScalaConsoleInfo, ScalaReplBundle}

class ScalaConsoleNewLineAction extends EditorAction(new EditorWriteActionHandler(true) {

  override def isEnabledForCaret(editor: Editor, caret: Caret, dataContext: DataContext): Boolean =
    ScalaConsoleInfo.isConsole(editor) &&
      getEnterHandler.isEnabled(editor, caret, dataContext)

  override def executeWriteAction(editor: Editor, caret: Caret, dataContext: DataContext): Unit =
    getEnterHandler.execute(editor, caret, dataContext)

  private def getEnterHandler: EditorActionHandler =
    EditorActionManager.getInstance.getActionHandler(IdeActions.ACTION_EDITOR_ENTER)
}) {
  locally {
    val presentation = getTemplatePresentation
    presentation.setText(ScalaReplBundle.message("scalaconsole.new.line.in.repl"))
    presentation.setDescription(ScalaReplBundle.message("scalaconsole.new.line.in.repl"))
  }
}