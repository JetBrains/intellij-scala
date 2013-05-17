package org.jetbrains.sbt
package project

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.project.Project
import com.intellij.icons.AllIcons
import com.intellij.openapi.externalSystem.service.project.wizard.AbstractExternalProjectImportProvider

/**
 * @author Pavel Fatin
 */
class SbtProjectImportProvider(builder: SbtProjectImportBuilder) extends AbstractExternalProjectImportProvider(builder) {
  override def getId = "SBT"

  override def getName = "SBT project"

  override def getIcon = AllIcons.General.Add

  override def canImport(entry: VirtualFile, project: Project) = {
    !entry.isDirectory && entry.getName == "build.sbt" ||
      (entry.isDirectory &&
        (Option(entry.findChild("build.sbt")).exists(!_.isDirectory) ||
          Option(entry.findChild("project")).exists(_.isDirectory)))
  }
}
