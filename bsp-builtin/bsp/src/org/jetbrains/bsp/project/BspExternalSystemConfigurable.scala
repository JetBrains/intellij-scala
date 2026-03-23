package org.jetbrains.bsp.project

import com.intellij.ide.ui.search.TraverseUIMode
import com.intellij.openapi.externalSystem.ExternalSystemManager
import com.intellij.openapi.externalSystem.service.settings.AbstractExternalSystemConfigurable
import com.intellij.openapi.externalSystem.util.{ExternalSystemApiUtil, ExternalSystemSettingsControl}
import com.intellij.openapi.project.Project
import org.jetbrains.bsp._
import org.jetbrains.bsp.settings._
import org.jetbrains.plugins.scala.help.ScalaWebHelpProvider

import javax.swing.JComponent

class BspExternalSystemConfigurable(project: Project)
  extends AbstractExternalSystemConfigurable[BspProjectSettings, BspProjectSettingsListener, BspSettings](project, BSP.ProjectSystemId) {

  override def createProjectSettingsControl(settings: BspProjectSettings): ExternalSystemSettingsControl[BspProjectSettings] =
    new BspProjectSettingsControl(settings)

  override def createSystemSettingsControl(settings: BspSettings): ExternalSystemSettingsControl[BspSettings] =
    new BspSystemSettingsControl(settings)

  override def newProjectSettings(): BspProjectSettings = new BspProjectSettings

  override def getId: String = "bsp.project.settings.configurable"

  override def getHelpTopic: String =
    ScalaWebHelpProvider.HelpPrefix + "bsp-support.html"


  override def createComponent(): JComponent = {
    if (project.isDefault && TraverseUIMode.getInstance().isActive) {
      // During buildIntellijOptionsIndex (aka traverseUI), we also want the project section to be indexed.
      // Unfortunately, that section of the ui is only created if there are BspSettings linked to the project of BspExternalSystemConfigurable
      // For the project itself intellij uses the "Default Project (Wizard Template)" but BspSettings are only linked during importing.
      // So in the case that we are in TraverseUIMode we link a default BspSettings to the default project.
      // In that way createProjectSettingsControl is called and the full UI created and indexed.
      val bspSettings = getBspSettingsForDefaultProject(project)
      if (bspSettings.getLinkedProjectSettings("") == null) {
        val settings = new BspProjectSettings
        settings.setExternalProjectPath("")
        bspSettings.linkProject(settings)
      }
    }
    super.createComponent()
  }

  override def disposeUIResources(): Unit = {
    // Remove the external project we linked in createComponent, otherwise they will mangle
    // the default project in our dev idea
    if (project.isDefault && TraverseUIMode.getInstance().isActive) {
      getBspSettingsForDefaultProject(project).unlinkExternalProject("")
    }
    super.disposeUIResources()
  }


  private def getBspSettingsForDefaultProject(project: Project): BspSettings = {
    assert(project.isDefault)
    val manager = ExternalSystemApiUtil.getManager(BSP.ProjectSystemId)
      .asInstanceOf[ExternalSystemManager[BspProjectSettings, BspProjectSettingsListener, BspSettings, ?, ?]]
    manager.getSettingsProvider.fun(project)
  }
}