package org.jetbrains.plugins.scala.worksheet.actions

import com.intellij.openapi.actionSystem.{AnActionEvent, CommonDataKeys, DataContext}
import com.intellij.openapi.editor.Editor
import org.jetbrains.plugins.scala.extensions.{OptionExt, inReadAction}
import org.jetbrains.plugins.scala.worksheet.WorksheetFile
import org.jetbrains.plugins.scala.worksheet.ui.printers.WorksheetEditorPrinterFactory

trait WorksheetAction {

  protected def updatePresentationEnabled(e: AnActionEvent): Unit = {
    val enabled = isActionEnabled(e)
    e.getPresentation.setEnabled(enabled)
  }

  protected def isActionEnabledForFile(file: WorksheetFile) = true

  private def isActionEnabled(e: AnActionEvent): Boolean =
    inReadAction {
      val selectedFile = getCurrentScalaWorksheetEditorAndFile(e).map(_._2)
      selectedFile.exists(isActionEnabledForFile)
    }

  protected final def getCurrentScalaWorksheetEditorAndFile(event: AnActionEvent): Option[(Editor, WorksheetFile)] =
    getCurrentScalaWorksheetEditorAndFile(event.getDataContext)

  protected final def getCurrentScalaWorksheetEditorAndFile(context: DataContext): Option[(Editor, WorksheetFile)] = {
    for {
      editorFromContext <- Option(CommonDataKeys.EDITOR.getData(context))
      file <- Option(CommonDataKeys.PSI_FILE.getData(context)).filterByType[WorksheetFile]
    } yield {
      //When worksheet viewer editor is focused we want top-panel actions to work anyway
      //For that we need to return the original editor, because most of the code in other places expects it instead of viewer
      val originalEditor = WorksheetEditorPrinterFactory.getOriginalEditor(editorFromContext)
      val editor = originalEditor.getOrElse(editorFromContext)
      (editor, file)
    }
  }
}
