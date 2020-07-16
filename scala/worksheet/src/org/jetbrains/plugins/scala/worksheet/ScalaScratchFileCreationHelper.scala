package org.jetbrains.plugins.scala.worksheet

import com.intellij.ide.scratch.ScratchFileCreationHelper
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.project.Project

final class ScalaScratchFileCreationHelper extends ScratchFileCreationHelper {

  override def prepareText(project: Project, context: ScratchFileCreationHelper.Context, dataContext: DataContext): Boolean = {
    context.fileExtension = WorksheetFileType.getDefaultExtension
    context.language = WorksheetLanguage.INSTANCE
    // TODO: check if it works, for scala 2 & 3
    // TODO: what to do with already created scratch files with .scalaa extension?
    //  maybe add to the platform some "file type substitutor"?
    super.prepareText(project, context, dataContext)
  }

  override def beforeCreate(project: Project, context: ScratchFileCreationHelper.Context): Unit =
    super.beforeCreate(project, context)
}
