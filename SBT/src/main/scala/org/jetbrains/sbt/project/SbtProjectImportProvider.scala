package org.jetbrains.sbt
package project

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.project.Project
import com.intellij.openapi.externalSystem.service.project.wizard.AbstractExternalProjectImportProvider

/**
 * @author Pavel Fatin
 */
class SbtProjectImportProvider(builder: SbtProjectImportBuilder)
  extends AbstractExternalProjectImportProvider(builder, SbtProjectSystem.Id) {

  override def getId = Sbt.Name

  override def getName = Sbt.ProjectDescription

  override def getIcon = Sbt.Icon

  override def canImport(entry: VirtualFile, project: Project) =
    SbtProjectImportProvider.canImport(entry)

  override def getPathToBeImported(file: VirtualFile) =
    if (file.isDirectory) file.getPath else file.getParent.getPath
}

object SbtProjectImportProvider {
  def canImport(entry: VirtualFile): Boolean = {
    !entry.isDirectory && entry.getName == Sbt.BuildFile ||
      (entry.isDirectory &&
        (Option(entry.findChild(Sbt.BuildFile)).exists(!_.isDirectory) ||
          Option(entry.findChild(Sbt.ProjectDirectory)).exists(_.isDirectory)))
  }
}