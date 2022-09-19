package org.jetbrains.sbt
package project

import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.service.project.ProjectDataManager
import com.intellij.openapi.externalSystem.service.project.wizard.AbstractExternalProjectImportBuilder
import com.intellij.openapi.project.Project
import org.jetbrains.sbt.project.SbtImportControl.SbtImportControlFactory

import java.io.File
import javax.swing.Icon

class SbtProjectImportBuilder
  extends AbstractExternalProjectImportBuilder[SbtImportControl](ProjectDataManager.getInstance(), SbtImportControlFactory, SbtProjectSystem.Id) {

  override def getName: String = Sbt.Name

  override def getIcon: Icon = Sbt.Icon

  override def doPrepare(context: WizardContext): Unit = {}

  override def beforeCommit(dataNode: DataNode[ProjectData], project: Project): Unit = {}

  override def getExternalProjectConfigToUse(file: File): File = file

  override def applyExtraSettings(context: WizardContext): Unit = {}
}
