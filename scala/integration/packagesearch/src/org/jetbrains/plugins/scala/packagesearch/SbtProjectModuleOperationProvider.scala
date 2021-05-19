package org.jetbrains.plugins.scala.packagesearch


import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.jetbrains.packagesearch.intellij.plugin.extensibility.{AbstractProjectModuleOperationProvider, DependencyOperationMetadata, ProjectModuleType}
import org.jetbrains.plugins.scala.packagesearch.utils.SbtCommon.refreshSbtProject
import org.jetbrains.plugins.scala.packagesearch.utils.SbtProjectModuleType

class SbtProjectModuleOperationProvider extends AbstractProjectModuleOperationProvider {
  override def hasSupportFor(project: Project, psiFile: PsiFile): Boolean = {
    val file = psiFile.getVirtualFile
    if (file == null) return false
    if (file.getExtension == "sbt") return true
    false
  }

  override def hasSupportFor(projectModuleType: ProjectModuleType): Boolean = projectModuleType match {
    case SbtProjectModuleType => true
    case _ => false
  }

}
