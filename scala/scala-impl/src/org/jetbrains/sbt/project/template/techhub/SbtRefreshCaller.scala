package org.jetbrains.sbt.project.template.techhub

import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.externalSystem.service.execution.ProgressExecutionMode
import com.intellij.openapi.externalSystem.service.project.wizard.AbstractExternalModuleBuilder
import com.intellij.openapi.externalSystem.settings.{AbstractExternalSystemSettings, ExternalSystemSettingsListener}
import com.intellij.openapi.externalSystem.util.{ExternalSystemApiUtil, ExternalSystemUtil}
import com.intellij.openapi.project.Project
import org.jetbrains.sbt.project.SbtProjectSystem
import org.jetbrains.sbt.project.settings.SbtProjectSettings

/**
  * User: Dmitry.Naydanov
  * Date: 22.02.17.
  */
trait SbtRefreshCaller {
  this: AbstractExternalModuleBuilder[SbtProjectSettings] => 
  
  def callForRefresh(project: Project) {
    val runnable = new Runnable {
      override def run(): Unit = {

        val settings =
          ExternalSystemApiUtil.getSettings(project, SbtProjectSystem.Id).
            asInstanceOf[AbstractExternalSystemSettings[_ <: AbstractExternalSystemSettings[_, SbtProjectSettings, _],
            SbtProjectSettings, _ <: ExternalSystemSettingsListener[SbtProjectSettings]]]

        getExternalProjectSettings setExternalProjectPath getContentEntryPath
        settings linkProject getExternalProjectSettings

        ExternalSystemUtil.refreshProject(project, SbtProjectSystem.Id, getContentEntryPath,
          false, ProgressExecutionMode.IN_BACKGROUND_ASYNC)
      }
    }

    ExternalSystemUtil.invokeLater(project, ModalityState.NON_MODAL, runnable)
  }
}
