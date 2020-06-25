package org.jetbrains.plugins.scala
package lang
package parser

import com.intellij.ide.scratch.ScratchUtil
import com.intellij.lang.Language
import com.intellij.openapi.module.{Module, ModuleManager, ModuleUtilCore}
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.LanguageSubstitutor
import org.jetbrains.plugins.scala.project.ModuleExt
import org.jetbrains.plugins.scala.worksheet.settings.WorksheetFileSettings

final class ScalaLanguageSubstitutor extends LanguageSubstitutor {

   override def getLanguage(file: VirtualFile, project: Project): Language =
    moduleForFile(file, project) match {
      case module: Module if module.hasScala3 => Scala3Language.INSTANCE
      case _                                  => null
    }

  private def moduleForFile(file: VirtualFile, project: Project): Module =
    ModuleUtilCore.findModuleForFile(file, project) match {
      case null =>
        if (isScalaScratchFile(file))
          WorksheetFileSettings.getModuleForScratchFile(file, project).orNull
        else
          null
      case module => module
    }

  private def isScalaScratchFile(file: VirtualFile): Boolean =
    file.getExtension == ScalaLowerCase && ScratchUtil.isScratch(file)
}
