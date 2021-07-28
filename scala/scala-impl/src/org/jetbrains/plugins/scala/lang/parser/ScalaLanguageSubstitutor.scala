package org.jetbrains.plugins.scala
package lang
package parser

import com.intellij.lang.Language
import com.intellij.openapi.module.{Module, ModuleUtilCore}
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.LanguageSubstitutor
import com.intellij.testFramework.LightVirtualFile
import org.jetbrains.annotations.TestOnly
import org.jetbrains.plugins.scala.lang.parser.ScalaLanguageSubstitutor.looksLikeScala3LibJar
import org.jetbrains.plugins.scala.project.ModuleExt

import scala.util.matching.Regex

final class ScalaLanguageSubstitutor extends LanguageSubstitutor {

  /** @note for worksheet language substitutions see<br>
   *        [[org.jetbrains.plugins.scala.worksheet.WorksheetLanguageSubstitutor]] */
  override def getLanguage(file: VirtualFile, project: Project): Language = {
    val assignedLanguage = file match {
      // primary case: language injected into the Scala REPL file,
      // see org.jetbrains.plugins.scala.console.ScalaLanguageConsole.Helper
      case lightFile: LightVirtualFile => lightFile.getLanguage
      case _                           => null
    }
    val substituted = if (assignedLanguage != null)
      assignedLanguage
    else if (ScalaFileType.INSTANCE.isMyFileType(file))
      ModuleUtilCore.findModuleForFile(file, project) match {
        case module: Module if module.hasScala3 => Scala3Language.INSTANCE
        // TODO For library sources, determine whether a .scala file (possibly in a JAR) is associated with a .tasty file (possibly in a JAR)
        case _ => if (looksLikeScala3LibJar(file.getPath)) Scala3Language.INSTANCE else null
      }
    else
      null
    substituted
  }
}

private object ScalaLanguageSubstitutor {

  @TestOnly
  def looksLikeScala3LibJar(path: String): Boolean =
    Scala3LibrarySourcePath.matches(path)

  // examples:
  // scala3-library_3-3.0.0-sources.jar
  // scala3-compiler_3-3.0.1-sources.jar
  // ...
  // scalatest-core_3-3.2.9-sources.jar
  // airframe-surface_3-21.5.4-sources.jar
  private val Scala3LibrarySourcePath: Regex = raw".*_3-\d+\.\d+\.\d+(-[\w\d-]+?)?-sources.jar!/.*".r
}
