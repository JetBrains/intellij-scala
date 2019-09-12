package org.jetbrains.sbt
package project

import com.intellij.openapi.externalSystem.service.project.wizard.AbstractExternalProjectImportProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import javax.swing.Icon

/**
 * @author Pavel Fatin
 */
final class SbtProjectImportProvider(builder: SbtProjectImportBuilder)
  extends AbstractExternalProjectImportProvider(builder, SbtProjectSystem.Id) {

  def this() = {
    this(new SbtProjectImportBuilder())
  }

  override def getId: String = Sbt.Name

  override def getName: String = Sbt.Name

  override def getIcon: Icon = Sbt.Icon

  override def canImport(entry: VirtualFile, project: Project): Boolean =
    SbtProjectImportProvider.canImport(entry)

  override def getPathToBeImported(file: VirtualFile): String =
    SbtProjectImportProvider.projectRootPath(file)
}

object SbtProjectImportProvider {

  import language.SbtFileType.isMyFileType

  def canImport(file: VirtualFile): Boolean = file match {
    case null => false
    case directory if directory.isDirectory =>
      directory.getName == Sbt.ProjectDirectory ||
        containsSbtProjectDirectory(directory) ||
        containsSbtBuildFile(directory)
    case _ => isMyFileType(file)
  }

  private def containsSbtProjectDirectory(directory: VirtualFile) =
    directory.findChild(Sbt.ProjectDirectory) match {
      case null => false
      case projectDirectory =>
        projectDirectory.containsFile(Sbt.PropertiesFile) ||
          containsSbtBuildFile(projectDirectory)
    }

  private def containsSbtBuildFile(directory: VirtualFile) =
    directory.getChildren.exists(isMyFileType)

  def projectRootPath(file: VirtualFile): String = {
    val root = if (file.isDirectory && file.getName != Sbt.ProjectDirectory) file
    else file.getParent

    root.getPath
  }

}