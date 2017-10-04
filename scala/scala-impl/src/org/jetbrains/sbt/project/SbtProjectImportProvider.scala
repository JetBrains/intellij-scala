package org.jetbrains.sbt
package project

import javax.swing.Icon

import com.intellij.openapi.externalSystem.service.project.wizard.AbstractExternalProjectImportProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

/**
 * @author Pavel Fatin
 */
class SbtProjectImportProvider(builder: SbtProjectImportBuilder)
  extends AbstractExternalProjectImportProvider(builder, SbtProjectSystem.Id) {

  override def getId: String = Sbt.Name

  override def getName: String = Sbt.Name

  override def getIcon: Icon = Sbt.Icon

  override def canImport(entry: VirtualFile, project: Project): Boolean =
    SbtProjectImportProvider.canImport(entry)

  override def getPathToBeImported(file: VirtualFile): String =
    SbtProjectImportProvider.projectRootOf(file).getPath
}

object SbtProjectImportProvider {

  def canImport(entry: VirtualFile): Boolean = {
    if (entry.isDirectory) {
      entry.getName == Sbt.ProjectDirectory ||
        containsSbtProjectDirectory(entry) ||
        containsSbtBuildFile(entry)

    } else {
      Sbt.isSbtFile(entry.getName)
    }
  }

  private def containsSbtProjectDirectory(file: VirtualFile) =
    Option(file.findChild(Sbt.ProjectDirectory))
      .exists { projectDir =>
        projectDir.containsFile(Sbt.PropertiesFile) ||
        containsSbtBuildFile(projectDir)
      }

  private def containsSbtBuildFile(dir: VirtualFile) =
    dir.getChildren.exists(child => Sbt.isSbtFile(child.getName))

  def projectRootOf(entry: VirtualFile): VirtualFile = {
    if (entry.isDirectory) {
      if (entry.getName == Sbt.ProjectDirectory) entry.getParent else entry
    } else {
      entry.getParent
    }
  }
}