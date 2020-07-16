package org.jetbrains.plugins.scala.worksheet

import com.intellij.lang.Language
import com.intellij.openapi.module.{Module, ModuleUtilCore}
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.LanguageSubstitutor
import org.jetbrains.plugins.scala.{Scala3Language, ScalaLanguage, ScalaLowerCase}
import org.jetbrains.plugins.scala.project.ModuleExt

final class ScalaWorksheetLanguageSubstitutor extends LanguageSubstitutor {

  override def getLanguage(file: VirtualFile, project: Project): Language = {
    val module  = ModuleUtilCore.findModuleForFile(file, project)
    val isScala3 = module match {
      case m: Module => m.hasScala3
      case _         => false
    }

    if (isScala3)
      null // Scala3Language.INSTANCE
    else
      null // ScalaLanguage.INSTANCE
  }
}
