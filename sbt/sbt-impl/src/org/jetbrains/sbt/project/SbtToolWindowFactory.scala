package org.jetbrains.sbt.project

import com.intellij.openapi.externalSystem.service.task.ui.AbstractExternalSystemToolWindowFactory
import com.intellij.openapi.externalSystem.settings.AbstractExternalSystemSettings
import com.intellij.openapi.project.Project
import org.jetbrains.sbt.settings.SbtSettings

class SbtToolWindowFactory extends AbstractExternalSystemToolWindowFactory(SbtProjectSystem.Id) {

  override def getSettings(project: Project): AbstractExternalSystemSettings[_, _, _] =
    SbtSettings.getInstance(project)
}