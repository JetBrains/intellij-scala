package org.jetbrains.plugins.scala
package annotator

import com.intellij.codeInsight.daemon.ProblemHighlightFilter
import com.intellij.ide.scratch.ScratchUtil
import com.intellij.openapi.roots.{JavaProjectRootsUtil, ProjectRootManager}
import com.intellij.psi.PsiFile
import org.jetbrains.plugins.scala.console.ScalaConsoleInfo
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile

final class ScalaProblemHighlightFilter extends ProblemHighlightFilter {

  override def shouldHighlight(file: PsiFile): Boolean = file match {
    case file: ScalaFile =>
      isInSourceRoots(file) || isSpecialFile(file)
    case _ => true
  }

  override def shouldProcessInBatch(file: PsiFile): Boolean = {
    val shouldHighlight = ProblemHighlightFilter.shouldHighlightFile(file)

    shouldHighlight && !isLibraryAndNotSource(file)
  }


  private def isSpecialFile(file: ScalaFile): Boolean = {
    file.getFileType != ScalaFileType.INSTANCE ||
      // looks like outdated check, ammonite files should have ".sc" extension,
      // so file.getFileType != ScalaFileType.INSTANCE should be true in this case
      //AmmoniteUtil.isAmmoniteFile(file) ||
      ScratchUtil.isScratch(file.getVirtualFile) ||
      file.isScriptFile ||
      ScalaConsoleInfo.isConsole(file)
  }

  private def isInSourceRoots(file: ScalaFile): Boolean =
    !JavaProjectRootsUtil.isOutsideJavaSourceRoot(file)

  //file may be both source and library source
  private def isLibraryAndNotSource(file: PsiFile): Boolean = {
    val fileIndex = ProjectRootManager.getInstance(file.getProject).getFileIndex
    Option(file.getVirtualFile).exists { vFile =>
      fileIndex.isInLibrary(vFile) && !fileIndex.isInSourceContent(vFile)
    }
  }
}
