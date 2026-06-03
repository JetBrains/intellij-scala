package org.jetbrains.sbt
package project

import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.service.project.ProjectDataManager
import com.intellij.openapi.externalSystem.service.project.wizard.AbstractExternalProjectImportBuilder
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.project.external.SdkUtils
import org.jetbrains.sbt.project.SbtImportControl.SbtImportControlFactory
import org.jetbrains.sbt.project.data.SbtProjectData

import javax.swing.Icon
import scala.jdk.CollectionConverters.CollectionHasAsScala

class SbtProjectImportBuilder
  extends AbstractExternalProjectImportBuilder[SbtImportControl](
    ProjectDataManager.getInstance(),
    SbtImportControlFactory,
    SbtProjectSystem.Id
  ) {

  override def getName: String = Sbt.Name

  override def getIcon: Icon = Sbt.Icon

  override def doPrepare(context: WizardContext): Unit = {}

  override def beforeCommit(dataNode: DataNode[ProjectData], project: Project): Unit = {}

  override def getExternalProjectConfigToUse(file: java.io.File): java.io.File = file

  override def applyExtraSettings(context: WizardContext): Unit = {
    getList.asScala.foreach { projectData =>
      ExternalSystemApiUtil.findAll(projectData, SbtProjectData.Key).asScala.headOption match {
        case Some(sbtProjectData) =>
          val sdkReference = sbtProjectData.getData.jdk
          val eelDescriptor = context.getProjectDirectory.eelDescriptor
          SdkUtils.findProjectSdk(sdkReference, eelDescriptor).foreach(context.setProjectJdk)
        case _ =>
      }
    }
  }
}
