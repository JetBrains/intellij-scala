package org.jetbrains.plugins.cbt.project

import java.io.File
import javax.swing.Icon

import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.service.project.ProjectDataManager
import com.intellij.openapi.externalSystem.service.project.wizard.AbstractExternalProjectImportBuilder
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.cbt.CBT

class CbtProjectImportBuilder(projectDataManager: ProjectDataManager)
  extends AbstractExternalProjectImportBuilder[CbtImportControl](projectDataManager,
    new CbtImportControl(), CbtProjectSystem.Id) {

  def getName: String = "CBT"

  def getIcon: Icon = CBT.Icon

  def doPrepare(context: WizardContext): Unit = {}

  def beforeCommit(dataNode: DataNode[ProjectData], project: Project): Unit = {}

  def onProjectInit(project: Project): Unit = {}

  def getExternalProjectConfigToUse(file: File): File = file

  def applyExtraSettings(context: WizardContext): Unit = {}
}
