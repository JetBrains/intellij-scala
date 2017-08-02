package org.jetbrains.sbt
package project

import com.intellij.openapi.externalSystem.service.project.wizard.AbstractExternalProjectImportProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

/**
 * @author Pavel Fatin
 */
class SbtProjectImportProvider(builder: SbtProjectImportBuilder)
  extends AbstractExternalProjectImportProvider(builder, SbtProjectSystem.Id) {

  override def getId = Sbt.Name

  override def getName = Sbt.Name

  override def getIcon = Sbt.Icon

  override def canImport(entry: VirtualFile, project: Project): Boolean =
    SbtProjectImportProvider.canImport(entry)

  override def getPathToBeImported(file: VirtualFile): String =
    SbtProjectImportProvider.projectRootOf(file).getPath
}

object SbtProjectImportProvider {
  def canImport(entry: VirtualFile): Boolean = {
    if (entry.isDirectory) {
      entry.getName == Sbt.ProjectDirectory ||
              entry.containsDirectory(Sbt.ProjectDirectory) ||
              entry.containsFile(Sbt.BuildFile)

    } else {
      entry.getName == Sbt.BuildFile ||
        Sbt.isSbtFile(entry.getName)
    }
  }

  def projectRootOf(entry: VirtualFile): VirtualFile = {
    if (entry.isDirectory) {
      if (entry.getName == Sbt.ProjectDirectory) entry.getParent else entry
    } else {
      entry.getParent
    }
  }
}