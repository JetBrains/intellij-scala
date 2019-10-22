package org.jetbrains.plugins.scala
package worksheet

import com.intellij.ide.scratch.{ScratchFileService, ScratchRootType}
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.util.containers.ContainerUtil
import org.jetbrains.jps.model.java.JavaSourceRootType
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaFileImpl
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
      hasScratchRootType(file) && canBeTreatedAsWorksheet(file)
  }

  // not using matching or isInstanceOf because we do not want all ScalaFileImpl inheritors to inherit this property
  // NOTE: this is not the best decision if we want to extract worksheet module and only depend on Scala API
  // But it a necessity in current state, when ScalaFileImpl is a base parent for other file types
  // To workaround that we could probably create separate psi elements: ScalaFileBase and ScalaFile?
  private def canBeTreatedAsWorksheet(vFile: VirtualFile)(implicit project: Project): Boolean = {
    val psiFile = PsiManager.getInstance(project).findFile(vFile)
    if (psiFile != null && psiFile.getClass == classOf[ScalaFileImpl]) {
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

  def hasScratchRootType(file: VirtualFile): Boolean =
    ScratchFileService.getInstance().getRootType(file).isInstanceOf[ScratchRootType]

  def treatScratchFileAsWorksheet(implicit project: Project): Boolean =
    projectSettings.isTreatScratchFilesAsWorksheet

  private def projectSettings(implicit project: Project) =
    ScalaProjectSettings.getInstance(project)
}
