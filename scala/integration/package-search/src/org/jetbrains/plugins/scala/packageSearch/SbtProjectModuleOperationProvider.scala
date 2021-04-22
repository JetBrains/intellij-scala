package org.jetbrains.plugins.scala.packageSearch


import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.jetbrains.packagesearch.intellij.plugin.extensibility.{AbstractProjectModuleOperationProvider, DependencyOperationMetadata, ProjectModuleType}

class SbtProjectModuleOperationProvider extends AbstractProjectModuleOperationProvider {
  override def hasSupportFor(project: Project, psiFile: PsiFile): Boolean = {
    println("Start of SbtProjectModuleOperationProvider.hasSupportFor!")
    val file = psiFile.getVirtualFile
    print(file.getExtension)
    if (file == null) return false
    if (file.getExtension == "sbt") return true
    false
  }

  override def hasSupportFor(projectModuleType: ProjectModuleType): Boolean = projectModuleType match {
    case SbtProjectModuleType => true
    case _ => false
  }

  override def refreshProject(project: Project, virtualFile: VirtualFile): Unit = {
    print("refreshProject is not implemented!")
  }
}
