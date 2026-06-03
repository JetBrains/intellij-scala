package org.jetbrains.plugins.scala.worksheet

import com.intellij.ide.scratch.ScratchFileCreationHelper
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.project.Project
import org.apache.commons.lang3.StringUtils
import org.jetbrains.plugins.scala.{Scala3Language, ScalaLanguage}
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings
import org.jetbrains.plugins.scala.worksheet.ScalaScratchFileCreationHelper.worksheetScratchFileType

final class ScalaScratchFileCreationHelper extends ScratchFileCreationHelper {

  override def prepareText(
    project: Project,
    context: ScratchFileCreationHelper.Context,
    dataContext: DataContext
  ): Boolean = {
    if (ScalaProjectSettings.getInstance(project).isTreatScratchFilesAsWorksheet) {
      // ATTENTION: DIRTY HACK USED: modifying of parameter state can be unexpected to the caller
      // TODO: create a proper, clean API for this in IDEA platform
      context.language match {
        // this helper is also called for any dialect of Scala (e.g. SbtLanguage)
        // but we want to create treat only actual Scala strach files as worksheets SCL-16417
        case ScalaLanguage.INSTANCE | Scala3Language.INSTANCE =>
          val fileType = worksheetScratchFileType
          context.fileExtension = fileType.getDefaultExtension
          context.language = fileType.getLanguage
          true
        case _ =>
          false
      }
    } else if (StringUtils.isBlank(context.text)) {
      // TODO (minor): Running of scala scratch files in non-worksheet mode doesn't work now
      //  (and didn't work before)
      //val caretMarker = "CARET_MARKER"
      //val textOneLine = s"object Scratch {def main(args: Array[String]): Unit = {$caretMarker}}"
      //val text = ScratchFileCreationHelper.reformat(project, context.language, textOneLine)
      //context.caretOffset = text.indexOf(caretMarker)
      //context.text = text.replace(caretMarker, "")
      //true
      false
    } else {
      super.prepareText(project, context, dataContext)
    }
  }

  override def beforeCreate(project: Project, context: ScratchFileCreationHelper.Context): Unit =
    super.beforeCreate(project, context)
}

object ScalaScratchFileCreationHelper {
  val worksheetScratchFileType = WorksheetFileType
}