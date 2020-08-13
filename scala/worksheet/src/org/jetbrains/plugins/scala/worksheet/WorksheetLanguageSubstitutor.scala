package org.jetbrains.plugins.scala.worksheet

import com.intellij.ide.scratch.ScratchUtil
import com.intellij.lang.Language
import com.intellij.openapi.module.{Module, ModuleUtilCore}
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.LanguageSubstitutor
import org.jetbrains.plugins.scala.project.ModuleExt
import org.jetbrains.plugins.scala.worksheet.settings.WorksheetFileSettings

final class WorksheetLanguageSubstitutor extends LanguageSubstitutor {

  override def getLanguage(file: VirtualFile, project: Project): Language = {
    val module = moduleFor(file, project)
    if (module.exists(_.hasScala3))
      WorksheetLanguage3.INSTANCE
    else
      null
  }

  private def moduleFor(file: VirtualFile, project: Project): Option[Module] = {
    val module1 = Option(ModuleUtilCore.findModuleForFile(file, project))
    val module2 = module1.orElse(WorksheetFileSettings.getModuleForScratchFile(file, project))
    module2
  }

  private def isScalaScratchFile(file: VirtualFile): Boolean =
    file.getExtension == WorksheetFileType.getDefaultExtension && ScratchUtil.isScratch(file)
}