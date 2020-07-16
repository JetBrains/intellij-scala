package org.jetbrains.plugins.scala.worksheet

import com.intellij.psi.PsiFile
import org.jetbrains.plugins.scala.codeInspection.feature.ScalaCompilerSettingsProfileProvider
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.project.settings.ScalaCompilerSettingsProfile
import org.jetbrains.plugins.scala.worksheet.settings.WorksheetFileSettings

final class WorksheetScalaCompilerSettingsProfileProvider extends ScalaCompilerSettingsProfileProvider {

  override def provide(file: PsiFile): Option[ScalaCompilerSettingsProfile] =
    file match {
      case scalaFile: ScalaFile if scalaFile.isWorksheetFile =>
        Option(WorksheetFileSettings(scalaFile).getCompilerProfile)
      case _ =>
        None
    }
}
