package org.jetbrains.plugins.scala
package lang
package parser

import com.intellij.lang.Language
import com.intellij.openapi.module.{Module, ModuleUtilCore}
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.LanguageSubstitutor
import org.jetbrains.plugins.scala.project.ModuleExt

final class ScalaLanguageSubstitutor extends LanguageSubstitutor {

  /** @note for worksheet language substitutions see<br>
   *       [[org.jetbrains.plugins.scala.worksheet.WorksheetLanguageSubstitutor]] */
  override def getLanguage(file: VirtualFile, project: Project): Language =
    if (ScalaFileType.INSTANCE.isMyFileType(file))
      ModuleUtilCore.findModuleForFile(file, project) match {
        case module: Module if module.hasScala3 => Scala3Language.INSTANCE
        case _                                  => null
      }
    else
      null
}
