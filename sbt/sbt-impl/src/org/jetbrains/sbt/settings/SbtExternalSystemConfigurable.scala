package org.jetbrains.sbt.settings

import com.intellij.ide.ui.search.TraverseUIMode
import com.intellij.openapi.externalSystem.ExternalSystemManager
import com.intellij.openapi.externalSystem.service.settings.AbstractExternalSystemConfigurable
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.help.ScalaWebHelpProvider
import org.jetbrains.sbt.project.SbtProjectSystem
import org.jetbrains.sbt.project.settings.Context.Configuration
import org.jetbrains.sbt.project.settings._

import javax.swing.JComponent

class SbtExternalSystemConfigurable(project: Project)
  extends AbstractExternalSystemConfigurable[SbtProjectSettings, SbtProjectSettingsListener, SbtSettings](project, SbtProjectSystem.Id) {

  override def createProjectSettingsControl(settings: SbtProjectSettings): SbtProjectSettingsControl = new SbtProjectSettingsControl(Configuration, settings)

  override def createSystemSettingsControl(settings: SbtSettings): SbtSettingsControl = new SbtSettingsControl(settings)

  override def newProjectSettings(): SbtProjectSettings = new SbtProjectSettings()

  override def getId: String = "sbt.project.settings.configurable"

  override def getHelpTopic: String =
    ScalaWebHelpProvider.HelpPrefix + "sbt-support.html"

  override def createComponent(): JComponent = {
    if (project.isDefault && TraverseUIMode.getInstance().isActive) {
      // During buildIntellijOptionsIndex (aka traverseUI), we also want the project section to be indexed.
      // Unfortunately, that section of the ui is only created if there are SbtSettings linked to the project of SbtExternalSystemConfigurable
      // For the project itself intellij uses the "Default Project (Wizard Template)" but SbtSettings are only linked during importing.
      // So in the case that we are in TraverseUIMode we link a default SbtSettings to the default project.
      // In that way createProjectSettingsControl is called and the full UI created and indexed.
      val sbtSettings = getSbtSettingsForDefaultProject(project)
      if (sbtSettings.getLinkedProjectSettings("") == null) {
        val settings = SbtProjectSettings.defaultForNewProjectWizard
        settings.setExternalProjectPath("")
        sbtSettings.linkProject(settings)
      }
    }
    super.createComponent()
  }

  override def disposeUIResources(): Unit = {
    // Remove the external project we linked in createComponent, otherwise they will mangle
    // the default project in our dev idea
    if (project.isDefault && TraverseUIMode.getInstance().isActive) {
      getSbtSettingsForDefaultProject(project).unlinkExternalProject("")
    }
    super.disposeUIResources()
  }

  private def getSbtSettingsForDefaultProject(project: Project): SbtSettings = {
    assert(project.isDefault)
    val manager = ExternalSystemApiUtil.getManager(SbtProjectSystem.Id)
      .asInstanceOf[ExternalSystemManager[SbtProjectSettings, _, SbtSettings, _, _]]
    manager.getSettingsProvider.fun(project)
  }
}
