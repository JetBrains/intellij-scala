package org.jetbrains.plugins.scala.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.psi.{PsiElement, PsiFile, PsiJavaFile}
import org.jetbrains.annotations.{Nullable, TestOnly}
import org.jetbrains.plugins.scala.ScalaFileType
import org.jetbrains.plugins.scala.extensions.PsiFileExt
import org.jetbrains.plugins.scala.lang.psi.api.{ScFile, ScalaFile}
import org.jetbrains.plugins.scala.project.{ProjectExt, ProjectPsiElementExt}

/**
 * The highlighting mode as configured in [[ScalaProjectSettings]], without the IDE-level checks (trusted project,
 * per-file highlighting level), so the PSI can use it.
 *
 * @see [[ScalaHighlightingMode]]
 */
object ScalaHighlightingSettings {

  @TestOnly
  private[scala] var compilerHighlightingEnabledInTests: Boolean = false

  private def isInTestMode: Boolean = ApplicationManager.getApplication.isUnitTestMode

  def showCompilerErrorsScala2(project: Project): Boolean =
    compilerHighlightingEnabledInTests ||
      !isInTestMode && ScalaProjectSettings.getInstance(project).isCompilerHighlightingScala2

  def showCompilerErrorsScala3(project: Project): Boolean =
    compilerHighlightingEnabledInTests ||
      !isInTestMode && ScalaProjectSettings.getInstance(project).isCompilerHighlightingScala3

  /**
   * Whether the Scala 3 compiler reports the errors of the element's file, so the checks it covers can be skipped.
   * Files not supported by compiler-based highlighting, such as Twirl templates, keep the annotator's checks.
   */
  def showCompilerErrorsScala3(element: PsiElement): Boolean =
    showCompilerErrorsScala3(element.getProject) && element.isInScala3Module &&
      isShowErrorsFromCompilerEnabled(element.getContainingFile)

  /**
   * Whether compiler-based highlighting reports the errors of the file. Only regular Scala files, worksheets and
   * Java files in Scala 3 modules are supported, other files keep the annotator's checks.
   */
  def isShowErrorsFromCompilerEnabled(file: PsiFile): Boolean =
    file match {
      case scalaFile: ScalaFile => isShowErrorsFromCompilerEnabled(scalaFile)
      case javaFile: PsiJavaFile => isShowErrorsFromCompilerEnabled(javaFile)
      case _ => false
    }

  private def isShowErrorsFromCompilerEnabled(file: ScalaFile): Boolean = {
    val virtualFile = file match {
      case ScFile.VirtualFile(vFile) => vFile
      case _                         => return false
    }

    val project = file.getProject
    val isRegularScalaFile = virtualFile.getFileType == ScalaFileType.INSTANCE
    if (isLeftToAnnotator(file)) {
      false
    } else if (isRegularScalaFile) {
      file.isScala3File && showCompilerErrorsScala3(project) ||
        file.isScala2File && showCompilerErrorsScala2(project)
    } else if (file.isWorksheetFile) {
      // actually this should work for regular files as well
      file.isInScala3Module && showCompilerErrorsScala3(project) ||
        file.isInScalaModule && showCompilerErrorsScala2(project)
    } else {
      false
    }
  }

  private def isShowErrorsFromCompilerEnabled(javaFile: PsiJavaFile): Boolean =
    showCompilerErrorsScala3(javaFile.getProject) && javaFile.isInScala3Module

  def isBuiltInHighlightingDisabled(project: Project): Boolean = {
    val settings = ScalaProjectSettings.in(project)
    def isCompilerHighlightingOnly = (settings.isCompilerHighlightingScala2 || !project.hasScala2) && (settings.isCompilerHighlightingScala3 || !project.hasScala3)
    settings.isDisableInspections && isCompilerHighlightingOnly
  }

  /** Incremental highlighting (SCL-23216) is off until the current highlighting pass finishes */
  private[scala] var incrementalHighlightingSuppressed: Boolean = false

  def isIncrementalHighlightingEnabled(@Nullable project: Project): Boolean =
    !incrementalHighlightingSuppressed && project != null && ScalaProjectSettings.in(project).isIncrementalHighlighting

  /**
   * Mill build files are left to the annotator, like sbt build files: Mill rewrites them before compiling them, so they
   * cannot be compiled as written, and its build server never clears the errors it reports on them.
   */
  private def isLeftToAnnotator(file: PsiFile): Boolean = file match {
    case scalaFile: ScalaFile => scalaFile.isMillFile
    case _                    => false
  }
}
