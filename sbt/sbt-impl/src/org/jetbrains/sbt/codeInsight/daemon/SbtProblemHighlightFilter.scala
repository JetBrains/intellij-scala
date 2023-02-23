package org.jetbrains.sbt.codeInsight.daemon

import com.intellij.codeInsight.daemon.ProblemHighlightFilter
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.roots.{JavaProjectRootsUtil, ProjectRootManager}
import com.intellij.openapi.vfs.NonPhysicalFileSystem
import com.intellij.psi.{PsiCodeFragment, PsiFile}
import org.jetbrains.plugins.scala.project.ModuleExt
import org.jetbrains.sbt.language.SbtFile

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
      val isInSources = !JavaProjectRootsUtil.isOutsideJavaSourceRoot(file)
      val isInSourceRootOfBuildModule = isInSources && {
        val module = ModuleUtilCore.findModuleForPsiElement(file)
        module != null && module.isBuildModule
      }
      isInSourceRootOfBuildModule || isInRootOfContentRoots(file)
    case _ =>
      true
  }

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
