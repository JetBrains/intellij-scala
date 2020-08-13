package org.jetbrains.plugins.scala.worksheet

import com.intellij.lang.Language
import com.intellij.openapi.module.{Module, ModuleManager, ModuleUtilCore}
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.LanguageSubstitutor
import org.jetbrains.plugins.scala.project.ModuleExt
import org.jetbrains.plugins.scala.Scala3Language

final class ScalaWorksheetLanguageSubstitutor extends LanguageSubstitutor {

  override def getLanguage(file: VirtualFile, project: Project): Language =
    moduleForFile(file, project) match {
      case module: Module if module.hasScala3 => Scala3Language.INSTANCE
      case _                                  => null
    }

  private def moduleForFile(file: VirtualFile, project: Project): Module =
    ModuleUtilCore.findModuleForFile(file, project)
}
