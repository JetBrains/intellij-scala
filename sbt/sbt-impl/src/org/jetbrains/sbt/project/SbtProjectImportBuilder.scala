package org.jetbrains.sbt
package project

import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.service.project.ProjectDataManager
import com.intellij.openapi.externalSystem.service.project.wizard.AbstractExternalProjectImportBuilder
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.ProjectJdkTable
import org.jetbrains.plugins.scala.project.external.SdkUtils
import org.jetbrains.sbt.project.SbtImportControl.SbtImportControlFactory
import org.jetbrains.sbt.project.data.SbtProjectData
import org.jetbrains.sbt.project.template.wizard.buildSystem.scheduleDownloadSdk

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

  /**
   * Starts the download of the project JDK when the user picked a "Download JDK" item in the wizard.
   * The wizard only registers such a JDK; the download needs a project and starts here.
   */
  override def beforeCommit(dataNode: DataNode[ProjectData], project: Project): Unit =
    val selectedSdk =
      getControl(project).getProjectSettings.jdkName
        .flatMap(name => Option(ProjectJdkTable.getInstance.findJdk(name)))
    selectedSdk.foreach(scheduleDownloadSdk(_, project))

  /**
   * Runs the preview import. When [[ConfigurationException]] is thrown, removes the SDK prepared for download and rethrows the error.
   *
   * [[org.jetbrains.sbt.project.settings.SbtProjectSettingsControl#prepareJdk]] creates and registers the SDK when
   * applying the wizard settings. The download starts only if the preview import is successful (in [[beforeCommit]]),
   * so a failed preview leaves an SDK that needs cleanup.
   */
  override def ensureProjectIsDefined(wizardContext: WizardContext): Unit =
    try
      super.ensureProjectIsDefined(wizardContext)
    catch
      case e: ConfigurationException =>
        getControl(wizardContext.getProject).discardPreparedJdkDownload()
        throw e

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
