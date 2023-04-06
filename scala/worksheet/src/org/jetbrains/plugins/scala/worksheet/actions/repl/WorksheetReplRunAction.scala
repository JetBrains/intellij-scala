package org.jetbrains.plugins.scala.worksheet.actions.repl

import com.intellij.openapi.actionSystem.{AnAction, AnActionEvent, CustomShortcutSet}
import org.jetbrains.plugins.scala.worksheet.WorksheetFile
import org.jetbrains.plugins.scala.worksheet.actions.WorksheetAction
import org.jetbrains.plugins.scala.worksheet.actions.topmenu.RunWorksheetAction
import org.jetbrains.plugins.scala.worksheet.settings.WorksheetFileSettings

final class WorksheetReplRunAction extends AnAction with WorksheetAction {

  setShortcutSet(CustomShortcutSet.fromString("ctrl ENTER"))
  setInjectedContext(false)

  override def update(e: AnActionEvent): Unit =
    updatePresentationEnabled(e)

  override def actionPerformed(e: AnActionEvent): Unit = {
    for {(editor, psiFile) <- getCurrentScalaWorksheetEditorAndFile(e)} {
      RunWorksheetAction.runCompilerForEditor(editor, psiFile, auto = false)
    }
  }

  override protected def isActionEnabledForFile(file: WorksheetFile): Boolean =
    WorksheetFileSettings(file).isRepl
}