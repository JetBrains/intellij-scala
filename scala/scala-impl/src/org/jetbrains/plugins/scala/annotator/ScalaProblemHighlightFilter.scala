package org.jetbrains.plugins.scala
package annotator

import com.intellij.codeInsight.daemon.ProblemHighlightFilter
import com.intellij.ide.scratch.ScratchFileService
import com.intellij.openapi.roots.{JavaProjectRootsUtil, ProjectRootManager}
import com.intellij.psi.PsiFile
import org.jetbrains.plugins.scala.console.ScalaConsoleInfo
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.worksheet.ammonite.AmmoniteUtil

/**
 * @author Alefas
 * @since 15/12/15
 */
final class ScalaProblemHighlightFilter extends ProblemHighlightFilter {

  override def shouldHighlight(file: PsiFile): Boolean = file match {
    case file: ScalaFile if file.getFileType == ScalaFileType.INSTANCE && !AmmoniteUtil.isAmmoniteFile(file) => // can be for example sbt file type
      !JavaProjectRootsUtil.isOutsideJavaSourceRoot(file) ||
        ScratchFileService.isInScratchRoot(file.getVirtualFile) ||
        file.isScriptFile ||
        ScalaConsoleInfo.isConsole(file)
    case _ => true
  }

  override def shouldProcessInBatch(file: PsiFile): Boolean =
    ProblemHighlightFilter.shouldHighlightFile(file) && {
      file.getFileType != ScalaFileType.INSTANCE || {
        val virtualFile = file.getVirtualFile
        virtualFile == null ||
          !ProjectRootManager.getInstance(file.getProject).getFileIndex.isInLibrarySource(virtualFile)
      }
    }
}
