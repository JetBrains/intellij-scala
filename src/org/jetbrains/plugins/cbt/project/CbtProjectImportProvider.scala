package org.jetbrains.plugins.cbt.project

import com.intellij.openapi.externalSystem.service.project.wizard.AbstractExternalProjectImportProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.plugins.cbt.CBT
import org.jetbrains.sbt.Sbt

class CbtProjectImportProvider(builder: CbtProjectImportBuilder)
  extends AbstractExternalProjectImportProvider(builder, CbtProjectSystem.Id) {

  override def getId = "CBT"

  override def getName = "CBT"

  override def getIcon = Sbt.Icon

  override def canImport(entry: VirtualFile, project: Project): Boolean =
      CBT.isCbtModuleDir(entry)

  override def getPathToBeImported(file: VirtualFile): String =
    file.getParent.getPath
}