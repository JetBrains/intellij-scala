package org.jetbrains.sbt
package project

import java.io.File
import javax.swing.Icon

import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.service.project.ProjectDataManager
import com.intellij.openapi.externalSystem.service.project.wizard.AbstractExternalProjectImportBuilder
import com.intellij.openapi.project.Project

/**
 * @author Pavel Fatin
 */
class SbtProjectImportBuilder(projectDataManager: ProjectDataManager)
  extends AbstractExternalProjectImportBuilder[SbtImportControl](projectDataManager, new SbtImportControl(), SbtProjectSystem.Id) {

  def getName: String = Sbt.Name

  def getIcon: Icon = Sbt.Icon

  def doPrepare(context: WizardContext) {}

  def beforeCommit(dataNode: DataNode[ProjectData], project: Project) {}

  def onProjectInit(project: Project) {}

  def getExternalProjectConfigToUse(file: File): File = file

  def applyExtraSettings(context: WizardContext) {}
}
