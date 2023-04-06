package org.jetbrains.plugins.scala.worksheet.actions

import com.intellij.openapi.actionSystem.{AnActionEvent, CommonDataKeys, DataContext}
import com.intellij.openapi.editor.Editor
import org.jetbrains.plugins.scala.extensions.{OptionExt, inReadAction}
import org.jetbrains.plugins.scala.worksheet.WorksheetFile

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
      editor <- Option(CommonDataKeys.EDITOR.getData(context))
      file <- Option(CommonDataKeys.PSI_FILE.getData(context)).filterByType[WorksheetFile]
    } yield (editor, file)
  }
}
