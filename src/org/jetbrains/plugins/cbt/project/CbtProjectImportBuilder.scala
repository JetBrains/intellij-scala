package org.jetbrains.plugins.cbt.project

import java.io.File

import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.service.project.ProjectDataManager
import com.intellij.openapi.externalSystem.service.project.wizard.AbstractExternalProjectImportBuilder
import com.intellij.openapi.project.Project
import org.jetbrains.sbt.Sbt

class CbtProjectImportBuilder(projectDataManager: ProjectDataManager)
  extends AbstractExternalProjectImportBuilder[CbtImportControl](projectDataManager,
    new CbtImportControl(), CbtProjectSystem.Id) {

  def getName = "CBT"

  def getIcon = Sbt.Icon

  def doPrepare(context: WizardContext) {}

  def beforeCommit(dataNode: DataNode[ProjectData], project: Project) {}

  def onProjectInit(project: Project) {}

  def getExternalProjectConfigToUse(file: File): File = file

  def applyExtraSettings(context: WizardContext) {}
}
