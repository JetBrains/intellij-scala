package org.jetbrains.plugins.cbt.project

import javax.swing.Icon

import com.intellij.openapi.externalSystem.service.project.wizard.AbstractExternalProjectImportProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.plugins.cbt.CBT

class CbtProjectImportProvider(builder: CbtProjectImportBuilder)
  extends AbstractExternalProjectImportProvider(builder, CbtProjectSystem.Id) {

  override def getId: String = "CBT"

  override def getName: String = "CBT"

  override def getIcon: Icon = CBT.Icon

  override def canImport(entry: VirtualFile, project: Project): Boolean =
    CBT.isCbtModuleDir(entry) || CBT.isCbtBuildFile(entry)

  override def getPathToBeImported(file: VirtualFile): String =
    if (CBT.isCbtModuleDir(file))
      file.getParent.getPath
    else
      file.getParent.getParent.getPath
}