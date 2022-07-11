package org.jetbrains.plugins.scala.packagesearch


import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.jetbrains.packagesearch.intellij.plugin.extensibility.{AbstractAsyncProjectModuleOperationProvider, ProjectModuleType}
import org.jetbrains.plugins.scala.packagesearch.utils.SbtProjectModuleType

class SbtProjectModuleOperationProvider extends AbstractAsyncProjectModuleOperationProvider {
  override def hasSupportFor(project: Project, psiFile: PsiFile): Boolean = {
    val file = psiFile.getVirtualFile
    if (file == null || file.getExtension == null) return false
    if (file.getExtension.toLowerCase() == "sbt" || file.getExtension.toLowerCase() == "scala") return true
    false
  }

  override def hasSupportFor(projectModuleType: ProjectModuleType): Boolean = projectModuleType match {
    case SbtProjectModuleType => true
    case _ => false
  }
}
