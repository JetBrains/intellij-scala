package org.jetbrains.plugins.scala.lang.parser

import com.intellij.lang.Language
import com.intellij.openapi.module.{Module, ModuleUtilCore}
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.LanguageSubstitutor
import com.intellij.testFramework.LightVirtualFile
import org.jetbrains.annotations.TestOnly
import org.jetbrains.plugins.scala.{Scala3Language, ScalaFileType}
import org.jetbrains.plugins.scala.lang.parser.ScalaLanguageSubstitutor.looksLikeScala3LibSourcesJar
import org.jetbrains.plugins.scala.project.ModuleExt

import scala.util.matching.Regex

final class ScalaLanguageSubstitutor extends LanguageSubstitutor {

  /** @note for worksheet language substitutions see<br>
   *        org.jetbrains.plugins.scala.worksheet.WorksheetLanguageSubstitutor */
  override def getLanguage(file: VirtualFile, project: Project): Language = {
    val assignedLanguage = file match {
      // primary case: language injected into the Scala REPL file,
      // see org.jetbrains.plugins.scala.console.ScalaLanguageConsole.Helper
      case lightFile: LightVirtualFile => lightFile.getLanguage
      case _                           => null
    }
    val substituted = if (assignedLanguage != null)
      assignedLanguage
    else if (ScalaFileType.INSTANCE.isMyFileType(file)) {
      val module = ModuleUtilCore.findModuleForFile(file, project)
      if (module != null && module.hasScala3)
        Scala3Language.INSTANCE
      else if (looksLikeScala3LibSourcesJar(file.getPath))
      // TODO For library sources, determine whether a .scala file (possibly in a JAR) is associated with a .tasty file (possibly in a JAR)
        Scala3Language.INSTANCE
      else null
    }
    else null

    substituted
  }
}

private object ScalaLanguageSubstitutor {

  /**
   * @example
   *  - scala3-library_3-3.0.0-sources.jar
   *  - scala3-compiler_3-3.0.1-sources.jar
   *  - scalatest-core_3-3.2.9-sources.jar
   *  - airframe-surface_3-21.5.4-sources.jar
   *  - library-name_3-1.2.3-x.7.z
   *  - (see tests for more examples)
   */
  @TestOnly
  def looksLikeScala3LibSourcesJar(path: String): Boolean = {
    val end = path.indexOf("-sources.jar!/")
    if (end == -1)
      return false
    val suffixIdx = path.lastIndexOf(Scala3LibNameSuffix, end)
    if (suffixIdx == -1)
      return false
    val start = suffixIdx + Scala3LibNameSuffix.length
    if (start >= end)
      return false

    val libraryNameWithVersion = path.substring(start, end)
    SemVerSimplifiedRegex.matches(libraryNameWithVersion)
  }

  private val Scala3LibNameSuffix = "_3-"

  /**
   * See [[https://semver.org/]] for the format of library version.<br>
   * In particular see [[https://semver.org/#is-there-a-suggested-regular-expression-regex-to-check-a-semver-string regexp part]]<br>
   * (we use a simplified regexp)
   */
  private val SemVerSimplifiedRegex: Regex =
    raw"\d+\.\d+\.\d+(?:-[\w\d-]+(\.[\w\d-]+)*)?(?:\+[\w\d-]+(\.[\w\d-]+)*)?".r
}
