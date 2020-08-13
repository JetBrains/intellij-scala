package org.jetbrains.plugins.scala.worksheet

import com.intellij.ide.scratch.ScratchUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.util.containers.ContainerUtil
import org.jetbrains.jps.model.java.JavaSourceRootType
import org.jetbrains.plugins.scala.lang.psi.ScFileViewProvider
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings

object WorksheetUtils {

  def isWorksheetFile(project: Project, file: VirtualFile): Boolean = {
    val isExplicitWorksheet = WorksheetFileType.isMyFileType(file) && !isAmmoniteEnabled(project, file)
    isExplicitWorksheet ||
      ScratchUtil.isScratch(file) && canBeTreatedAsWorksheet(project, file)
  }

  private def canBeTreatedAsWorksheet(project: Project, vFile: VirtualFile): Boolean = {
    val psiFile = PsiManager.getInstance(project).findFile(vFile)
    if (psiFile != null && psiFile.getViewProvider.isInstanceOf[ScFileViewProvider]) {
      treatScratchFileAsWorksheet(project)
    } else {
      false
    }
  }

  def treatScratchFileAsWorksheet(project: Project): Boolean =
    settings(project).isTreatScratchFilesAsWorksheet

  def isAmmoniteEnabled(project: Project, file: VirtualFile): Boolean = {
    import ScalaProjectSettings.ScFileMode._
    settings(project).getScFileMode match {
      case Worksheet => false
      case Ammonite  => true
      case _         =>
        ProjectRootManager.getInstance(project).getFileIndex.isUnderSourceRootOfType(
          file,
          ContainerUtil.newHashSet(JavaSourceRootType.TEST_SOURCE)
        )
    }
  }

  private def settings(project: Project) =
    ScalaProjectSettings.getInstance(project)
}
