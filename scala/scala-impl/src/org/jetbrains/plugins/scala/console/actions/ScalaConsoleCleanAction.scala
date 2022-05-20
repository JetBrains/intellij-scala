package org.jetbrains.plugins.scala.console.actions

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.actionSystem.{EditorAction, EditorWriteActionHandler}
import com.intellij.openapi.editor.{Caret, Editor}
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.console.ScalaConsoleInfo

class ScalaConsoleCleanAction extends EditorAction(new EditorWriteActionHandler(false) {

  override def isEnabledForCaret(editor: Editor, caret: Caret, dataContext: DataContext): Boolean =
    ScalaConsoleInfo.isConsole(editor)

  override def executeWriteAction(editor: Editor, caret: Caret, dataContext: DataContext): Unit =
    ScalaConsoleInfo.getConsole(editor) match {
      case null =>
      case console => console.clear()
    }
}) {
  locally {
    val presentation = getTemplatePresentation
    presentation.setText(ScalaBundle.message("clean.scala.repl.content.menu.action.text"))
    presentation.setDescription(ScalaBundle.message("clean.scala.repl.content.menu.action.description"))
  }
}
