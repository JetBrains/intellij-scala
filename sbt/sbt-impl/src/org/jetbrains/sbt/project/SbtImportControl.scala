package org.jetbrains.sbt.project

import com.intellij.openapi.externalSystem.service.settings.AbstractImportFromExternalSystemControl
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.NotNullFactory
import org.jetbrains.sbt.project.settings._
import org.jetbrains.sbt.settings.{SbtSettings, SbtSettingsControl}

class SbtImportControl extends AbstractImportFromExternalSystemControl[
  SbtProjectSettings,
  SbtProjectSettingsListener,
  SbtSettings
](
  SbtProjectSystem.Id,
  SbtSettings.getInstance(ProjectManager.getInstance.getDefaultProject),
  SbtProjectSettings.default
) {

  /** Updates the wizard's JDK choices when the platform reports a change to the project path. */
  override def onLinkedProjectPathChange(path: String): Unit =
    sbtProjectSettingsControl.onLinkedProjectPathChange(path)

  /**
   * Asks the settings control to remove its prepared download after a preview error.
   *
   * @see [[SbtProjectSettingsControl#discardPreparedJdkDownload]]
   */
  def discardPreparedJdkDownload(): Unit =
    sbtProjectSettingsControl.discardPreparedJdkDownload()

  /** Returns the sbt settings control created by [[createProjectSettingsControl]]. */
  private def sbtProjectSettingsControl: SbtProjectSettingsControl =
    getProjectSettingsControl.asInstanceOf[SbtProjectSettingsControl]

  override def createProjectSettingsControl(settings: SbtProjectSettings) = new SbtProjectSettingsControl(Context.Wizard, settings)

  override def createSystemSettingsControl(settings: SbtSettings) = new SbtSettingsControl(settings)
}

object SbtImportControl {
  object SbtImportControlFactory extends NotNullFactory[SbtImportControl] {
    override def create(): SbtImportControl = new SbtImportControl
  }
}