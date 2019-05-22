package org.jetbrains.plugins.scala.worksheet.actions

import com.intellij.openapi.actionSystem.{AnAction, AnActionEvent, CustomShortcutSet, PlatformDataKeys}
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.psi.PsiDocumentManager
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.worksheet.cell.CellManager
import org.jetbrains.plugins.scala.worksheet.settings.WorksheetFileSettings

/**
  * User: Dmitry.Naydanov
  * Date: 04.09.18.
  */
class WorksheetRunCurrentCellAction extends AnAction with WorksheetAction {
  setShortcutSet(CustomShortcutSet.fromString("ctrl ENTER"))
  setInjectedContext(false)

  override def actionPerformed(e: AnActionEvent): Unit = {
    Option(PlatformDataKeys.FILE_EDITOR.getData(e.getDataContext)).foreach {
      fileEditor => 
        WorksheetFileHook.handleEditor(FileEditorManager.getInstance(e.getProject), fileEditor.getFile) {
          editor =>
            val offset = editor.getCaretModel.getCurrentCaret.getSelectionStart
            Option(PsiDocumentManager.getInstance(e.getProject).getCachedPsiFile(editor.getDocument)).foreach {
              file =>
                CellManager.getInstance(e.getProject).getCell(file, offset).flatMap(_.createRunAction).foreach(_.actionPerformed(e))
            }
        }
    }
  }

  override def update(e: AnActionEvent): Unit = {
    updateInner(e)
  }

  override def acceptFile(file: ScalaFile): Boolean = 
    WorksheetFileSettings.getRunType(file).isUsesCell
}

object WorksheetRunCurrentCellAction {
  val ACTION_INSTANCE = new WorksheetRunCurrentCellAction
}