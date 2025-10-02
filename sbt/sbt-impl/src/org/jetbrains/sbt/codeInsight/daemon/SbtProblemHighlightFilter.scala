package org.jetbrains.sbt.codeInsight.daemon

import com.intellij.codeInsight.daemon.ProblemHighlightFilter
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.NonPhysicalFileSystem
import com.intellij.psi.{PsiCodeFragment, PsiFile}
import org.jetbrains.plugins.scala.project.{ModuleExt, ProjectPsiFileExt}
import org.jetbrains.sbt.language.SbtFile
import org.jetbrains.sbt.project.SbtProjectImportStateService
import org.jetbrains.sbt.{SbtHighlightingUtil, SbtUtil}

/**
 * We need to highlight only those `*.sbt` files which are actual project definition: {{{
 *    project-root:
 *      build.sbt //should highlight
 *      project
 *        build.sbt //should highlight
 *      sub-project
 *        build.sbt //should highlight
 *        src/main/scala
 *          Main.scala
 *          build.sbt //should NOT highlight
 *        testdata
 *          build.sbt //should NOT highlight
 * }}}
 *
 * If it's some arbitrary `*.sbt` file, e.g. in testData folder, we shouldn't highlight it
 * It can contain references which can't be resolved
 * See also [[SbtProblemHighlightFilter]]
 */
final class SbtProblemHighlightFilter extends ProblemHighlightFilter {

  override def shouldHighlight(file: PsiFile): Boolean = file match {
    case file: SbtFile =>
      //File can be in the content root of non-build module or in the content root of build module:
      // root
      //   |-- project
      //     |-- build.sbt //should be highlighted
      //     |-- Dependencies.scala // should be highlighted but is handled by (ScalaProblemHighlightFilter)
      //   |-- build.sbt //should be highlighted
      //   |-- testdata
      //     |-- build.sbt //should NOT be highlighted
      //
      //But `file.module` will anyway resolve to a correct `build` module
      SbtProblemHighlightFilter.shouldHighlightSbtFile(file) && isInBuildModule(file) && isImported(file) ||
        ApplicationManager.getApplication.isUnitTestMode && SbtHighlightingUtil.isHighlightingOutsideBuildModuleEnabled(file.getProject)
    case _ =>
      true
  }

  private def isInBuildModule(sbtFile: SbtFile): Boolean =
    sbtFile.module.exists(_.isBuildModule)

  /**
   * We only track the import state of sbt projects. All other project types are considered imported.
   */
  private def isImported(file: SbtFile): Boolean = {
    val project = file.getProject
    !SbtUtil.isSbtProject(project) || SbtProjectImportStateService.instance(project).isImported(file)
  }
}

private[sbt] object SbtProblemHighlightFilter {

  def shouldHighlightSbtFile(sbtFile: SbtFile): Boolean =
    isInRootOfContentRoots(sbtFile)

  /** Some checks are similar to [[com.intellij.openapi.roots.JavaProjectRootsUtil.isOutsideJavaSourceRoot]] */
  private def isInRootOfContentRoots(psiFile: PsiFile): Boolean = {
    if (psiFile == null || !psiFile.isValid)
      return false
    if (psiFile.isInstanceOf[PsiCodeFragment])
      return false

    val file = psiFile.getVirtualFile
    if (file == null)
      return false
    if (file.getFileSystem.isInstanceOf[NonPhysicalFileSystem])
      return false

    val projectFileIndex = ProjectRootManager.getInstance(psiFile.getProject).getFileIndex
    val contentRoot = projectFileIndex.getContentRootForFile(file)
    if (contentRoot == null)
      return false

    val fileParent = psiFile.getParent
    if (fileParent == null)
      return false

    contentRoot == fileParent.getVirtualFile
  }
}
