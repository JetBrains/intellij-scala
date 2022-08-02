package org.jetbrains.sbt.project.template.wizard

import com.intellij.ide.fileTemplates.FileTemplateManager
import com.intellij.ide.util.projectWizard.ModuleBuilder
import com.intellij.ide.wizard.AbstractNewProjectWizardStep
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.{VfsUtil, VirtualFile}
import org.jetbrains.plugins.scala.extensions.inWriteAction

import scala.jdk.CollectionConverters.MapHasAsJava

package object buildSystem {

  private[buildSystem] def setProjectOrModuleSdk(
    project: Project,
    parentStep: AbstractNewProjectWizardStep,
    builder: ModuleBuilder,
    sdk: Option[Sdk]
  ): Unit = {
    val context = parentStep.getContext
    if (context.isCreatingNewProject) {
      // New project with a single module: set project JDK
      context.setProjectJdk(sdk.orNull)
    } else {
      // New module in an existing project: set module JDK
      val isSameSDK: Boolean = (for {
        jdk1 <- Option(ProjectRootManager.getInstance(project).getProjectSdk)
        jdk2 <- sdk
      } yield jdk1.getName == jdk2.getName).contains(true)
      builder.setModuleJdk(if (isSameSDK) null else sdk.orNull)
    }
  }

  private[buildSystem]
  def addScalaSampleCode(project: Project, path: String, isScala3: Boolean, packagePrefix: Option[String]): VirtualFile = {
    val manager = FileTemplateManager.getInstance(project)
    val (template, fileName) =
      if (isScala3) (manager.getInternalTemplate("scala3-sample-code.scala"), "main.scala")
      else (manager.getInternalTemplate("scala-sample-code.scala"), "Main.scala")
    val sourceCode = template.getText(packagePrefix.map(prefix => Map("PACKAGE_NAME" -> prefix)).getOrElse(Map.empty).asJava)

    inWriteAction {
      val fileDirectory = VfsUtil.createDirectoryIfMissing(path) match {
        case null => throw new IllegalStateException("Unable to create src directory")
        case fd => fd
      }
      val file: VirtualFile = fileDirectory.findOrCreateChildData(this, fileName)
      VfsUtil.saveText(file, sourceCode)
      file
    }
  }
}
