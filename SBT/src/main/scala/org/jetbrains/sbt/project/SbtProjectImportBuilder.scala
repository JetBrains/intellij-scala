package org.jetbrains.sbt
package project

import com.intellij.openapi.project.Project
import com.intellij.openapi.externalSystem.service.project.wizard.AbstractExternalProjectImportBuilder
import com.intellij.ide.util.projectWizard.WizardContext
import java.io.File
import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.service.project.manage.ProjectDataManager

/**
 * @author Pavel Fatin
 */
class SbtProjectImportBuilder(projectDataManager: ProjectDataManager)
  extends AbstractExternalProjectImportBuilder[SbtImportControl](projectDataManager, new SbtImportControl(), SbtProjectSystem.Id) {

  def getName = Sbt.Name

  def getIcon = Sbt.Icon

  def doPrepare(context: WizardContext) {}

  def beforeCommit(dataNode: DataNode[ProjectData], project: Project) {}

  def onProjectInit(project: Project) {}

  def getExternalProjectConfigToUse(file: File) = file

  def applyExtraSettings(context: WizardContext) {}
}
