package org.jetbrains.plugins.scala.worksheet

import com.intellij.lang.Language
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.LanguageSubstitutor
import com.intellij.util.SlowOperations
import org.jetbrains.plugins.scala.project.ModuleExt
import org.jetbrains.plugins.scala.worksheet.settings.WorksheetFileSettings

import scala.util.Using

final class WorksheetLanguageSubstitutor extends LanguageSubstitutor {

  override def getLanguage(file: VirtualFile, project: Project): Language = {
    val module = Using.resource(SlowOperations.knownIssue("SCL-21147")) { _ =>
      WorksheetFileSettings(project, file).getModule
    }
    if (module.exists(_.hasScala3))
      WorksheetLanguage3.INSTANCE
    else
      null
  }
}
