package org.jetbrains.plugins.scala
package worksheet

import com.intellij.ide.scratch.ScratchUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.util.containers.ContainerUtil
import org.jetbrains.jps.model.java.JavaSourceRootType
import org.jetbrains.plugins.scala.lang.psi.ScFileViewProvider
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings

//noinspection TypeAnnotation
object WorksheetFileType extends LanguageFileTypeBase(WorksheetLanguage.INSTANCE) {

  override def getDefaultExtension = "sc"

  // TODO worksheet logo
  override def getIcon = ScalaFileType.INSTANCE.getIcon

  def isWorksheetFile(file: VirtualFile)
                     (implicit project: Project): Boolean = {
    val isExplicitWorksheet = isMyFileExtension(file) && !isAmmoniteEnabled(file)
    isExplicitWorksheet ||
      ScratchUtil.isScratch(file) && canBeTreatedAsWorksheet(file)
  }

  private def canBeTreatedAsWorksheet(vFile: VirtualFile)(implicit project: Project): Boolean = {
    val psiFile = PsiManager.getInstance(project).findFile(vFile)
    if (psiFile != null && psiFile.getViewProvider.isInstanceOf[ScFileViewProvider]) {
      treatScratchFileAsWorksheet(project)
    } else {
      false
    }
  }

  def isAmmoniteEnabled(file: VirtualFile)
                       (implicit project: Project): Boolean = {
    import ScalaProjectSettings.ScFileMode._
    projectSettings.getScFileMode match {
      case Worksheet => false
      case Ammonite => true
      case _ =>
        ProjectRootManager.getInstance(project).getFileIndex.isUnderSourceRootOfType(
          file,
          ContainerUtil.newHashSet(JavaSourceRootType.TEST_SOURCE)
        )
    }
  }

  def treatScratchFileAsWorksheet(implicit project: Project): Boolean =
    projectSettings.isTreatScratchFilesAsWorksheet

  private def projectSettings(implicit project: Project) =
    ScalaProjectSettings.getInstance(project)
}
