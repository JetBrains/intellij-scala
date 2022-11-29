package org.jetbrains.plugins.scala.packagesearch


import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.jetbrains.packagesearch.intellij.plugin.extensibility.{AbstractAsyncProjectModuleOperationProvider, ProjectModuleType}
import org.jetbrains.plugins.scala.packagesearch.utils.SbtProjectModuleType

//noinspection UnstableApiUsage
class SbtProjectModuleOperationProvider extends AbstractAsyncProjectModuleOperationProvider {
  override def hasSupportFor(project: Project, psiFile: PsiFile): Boolean = {
    val file = psiFile.getVirtualFile
    if (file == null)
      return false

    val extension = file.getExtension
    if (extension == null)
      return false

    val extensionLowercase = extension.toLowerCase()
    extensionLowercase == "sbt" || extensionLowercase == "scala"
  }

  override def hasSupportFor(projectModuleType: ProjectModuleType): Boolean = projectModuleType match {
    case SbtProjectModuleType => true
    case _ => false
  }
}
