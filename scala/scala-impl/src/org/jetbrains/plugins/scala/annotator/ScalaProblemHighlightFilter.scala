package org.jetbrains.plugins.scala.annotator

import com.intellij.codeInsight.daemon.ProblemHighlightFilter
import com.intellij.ide.scratch.ScratchFileType
import com.intellij.openapi.roots.{JavaProjectRootsUtil, ProjectRootManager}
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import org.jetbrains.plugins.scala.ScalaFileType
import org.jetbrains.plugins.scala.console.ScalaConsoleInfo
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.worksheet.ammonite.AmmoniteUtil

/**
  * @author Alefas
  * @since  15/12/15
  */
class ScalaProblemHighlightFilter extends ProblemHighlightFilter {
  def shouldHighlight(file: PsiFile): Boolean = {
    file match {
      case scalaFile: ScalaFile if scalaFile.getFileType == ScalaFileType.INSTANCE && !AmmoniteUtil.isAmmoniteFile(scalaFile) => // can be for example sbt file type
        !JavaProjectRootsUtil.isOutsideJavaSourceRoot(file) || 
          scalaFile.getViewProvider.getFileType == ScratchFileType.INSTANCE || 
          scalaFile.isScriptFile || ScalaConsoleInfo.isConsole(file)
      case _ => true
    }
  }

  override def shouldProcessInBatch(file: PsiFile): Boolean = {
    if (ProblemHighlightFilter.shouldHighlightFile(file)) {
      if (file.getFileType == ScalaFileType.INSTANCE) {
        val vFile: VirtualFile = file.getVirtualFile
        if (vFile != null && ProjectRootManager.getInstance(file.getProject).getFileIndex.isInLibrarySource(vFile)) {
          return false
        }
      }
      true
    } else false
  }
}
