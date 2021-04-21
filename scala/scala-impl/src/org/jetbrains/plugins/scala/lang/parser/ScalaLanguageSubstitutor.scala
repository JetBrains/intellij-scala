package org.jetbrains.plugins.scala
package lang
package parser

import com.intellij.lang.Language
import com.intellij.openapi.module.{Module, ModuleUtilCore}
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.LanguageSubstitutor
import org.jetbrains.plugins.scala.lang.parser.ScalaLanguageSubstitutor.Scala3LibrarySourcePath
import org.jetbrains.plugins.scala.project.ModuleExt

final class ScalaLanguageSubstitutor extends LanguageSubstitutor {

  /** @note for worksheet language substitutions see<br>
   *       [[org.jetbrains.plugins.scala.worksheet.WorksheetLanguageSubstitutor]] */
  override def getLanguage(file: VirtualFile, project: Project): Language =
    if (ScalaFileType.INSTANCE.isMyFileType(file))
      ModuleUtilCore.findModuleForFile(file, project) match {
        case module: Module if module.hasScala3 => Scala3Language.INSTANCE
        // TODO For library sources, determine whether a .scala file (possibly in a JAR) is associated with a .tasty file (possibly in a JAR)
        case _ => if (Scala3LibrarySourcePath.matches(file.getPath)) Scala3Language.INSTANCE else null
      }
    else
      null
}

private object ScalaLanguageSubstitutor {
  private val Scala3LibrarySourcePath = raw".*3\.0\.0-RC\d-sources.jar!\/.*".r
}
