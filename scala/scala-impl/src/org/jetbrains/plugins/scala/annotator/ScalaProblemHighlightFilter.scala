package org.jetbrains.plugins.scala.annotator

import com.intellij.codeInsight.daemon.ProblemHighlightFilter
import com.intellij.ide.scratch.ScratchUtil
import com.intellij.openapi.roots.{JavaProjectRootsUtil, ProjectRootManager}
import com.intellij.psi.PsiFile
import org.jetbrains.plugins.scala.console.ScalaLanguageConsoleUtils
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.project.ScalaProjectConfigurationService
import org.jetbrains.sbt.language.SbtFile

/** @see [[ScalaProjectConfigurationService]] */
final class ScalaProblemHighlightFilter extends ProblemHighlightFilter {

  override def shouldHighlight(file: PsiFile): Boolean = file match {
    case _: SbtFile =>
      true // `.sbt` files are handled in `org.jetbrains.sbt.codeinsight.daemon.SbtProblemHighlightFilter`
    case file: ScalaFile =>
      if (isInSourceRoots(file)) {
        // don't show error highlighting in Scala file while project sync is in progress (SCL-13000, SCL-22458)
        !ScalaProjectConfigurationService.getInstance(file.getProject).isSyncInProgress
      } else isSpecialFile(file)
    case _ => true
  }

  override def shouldProcessInBatch(file: PsiFile): Boolean = {
    val shouldHighlight = ProblemHighlightFilter.shouldHighlightFile(file)

    shouldHighlight && !isLibraryAndNotSource(file)
  }

  /**
   * Relies on Java root types. Kotlin uses its own types along with Java ones.
   *
   * @see [[org.jetbrains.jps.model.java.JavaSourceRootType]]
   * @see [[org.jetbrains.kotlin.idea.base.util.ProjectStructureUtils.KOTLIN_AWARE_SOURCE_ROOT_TYPES]]
   */
  private def isInSourceRoots(file: ScalaFile): Boolean =
    !JavaProjectRootsUtil.isOutsideJavaSourceRoot(file)

  private def isSpecialFile(file: ScalaFile): Boolean =
    file.isWorksheetFile ||
      ScratchUtil.isScratch(file.getVirtualFile) ||
      ScalaLanguageConsoleUtils.isConsole(file)

  //file may be both source and library source
  private def isLibraryAndNotSource(file: PsiFile): Boolean = {
    val fileIndex = ProjectRootManager.getInstance(file.getProject).getFileIndex
    Option(file.getVirtualFile).exists { vFile =>
      fileIndex.isInLibrary(vFile) && !fileIndex.isInSourceContent(vFile)
    }
  }
}
