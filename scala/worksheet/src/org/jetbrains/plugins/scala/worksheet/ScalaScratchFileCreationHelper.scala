package org.jetbrains.plugins.scala.worksheet

import com.intellij.ide.scratch.ScratchFileCreationHelper
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.project.Project
import org.apache.commons.lang3.StringUtils
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings

final class ScalaScratchFileCreationHelper extends ScratchFileCreationHelper {

  override def prepareText(
    project: Project,
    context: ScratchFileCreationHelper.Context,
    dataContext: DataContext
  ): Boolean = {
    if (ScalaProjectSettings.getInstance(project).isTreatScratchFilesAsWorksheet) {
      // ATTENTION: DIRTY HACK USED: modifying of parameter state can be unexpected to the caller
      // TODO: create a proper, clean API for this in IDEA platform
      context.fileExtension = WorksheetFileType.getDefaultExtension
      context.language = WorksheetLanguage.INSTANCE
      true
    } else if (StringUtils.isBlank(context.text)) {
      val caretMarker = "CARET_MARKER"
      val textOneLine = s"object Scratch {def main(args: Array[String]): Unit = {$caretMarker}}"
      val text = ScratchFileCreationHelper.reformat(project, context.language, textOneLine)
      context.caretOffset = text.indexOf(caretMarker)
      context.text = text.replace(caretMarker, "")
      true
    } else {
      super.prepareText(project, context, dataContext)
    }
  }

  override def beforeCreate(project: Project, context: ScratchFileCreationHelper.Context): Unit =
    super.beforeCreate(project, context)
}