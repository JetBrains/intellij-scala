package org.jetbrains.plugins.scala
package worksheet
package actions

import com.intellij.ide.scratch.ScratchRootType
import com.intellij.openapi.actionSystem.{AnAction, AnActionEvent, CommonDataKeys}
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.util.text.StringUtil.notNullize
import org.jetbrains.plugins.scala.actions.ScalaActionUtil.enableAndShowIfInScalaFile
import org.jetbrains.plugins.scala.worksheet.ScalaScratchFileCreationHelper.worksheetScratchFileType

final class CreateLightWorksheetAction extends AnAction(
  WorksheetBundle.message("create.light.scala.worksheet.menu.action.text"),
  WorksheetBundle.message("create.light.scala.worksheet.menu.action.description"),
  /*icon = */ null
) {

  override def actionPerformed(event: AnActionEvent): Unit = {
    val project = event.getProject
    val text = event.getData(CommonDataKeys.EDITOR) match {
      case null => null
      case editor => editor.getSelectionModel.getSelectedText
    }

    val fileType = worksheetScratchFileType
    val file = ScratchRootType.getInstance.createScratchFile(
      project,
      s"scratch.${fileType.getDefaultExtension}",
      fileType.getLanguage,
      notNullize(text)
    )
    if (file != null) {
      FileEditorManager.getInstance(project).openFile(file, true)
    }
  }

  override def update(event: AnActionEvent): Unit = {
    enableAndShowIfInScalaFile(event)
  }
}
