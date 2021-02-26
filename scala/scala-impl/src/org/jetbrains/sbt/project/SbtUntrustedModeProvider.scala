package org.jetbrains.sbt.project

import com.intellij.openapi.externalSystem.importing.ImportSpecBuilder
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.externalSystem.service.project.UntrustedProjectModeProvider
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.project.Project
import org.jetbrains.sbt.settings.SbtSettings

class SbtUntrustedModeProvider extends UntrustedProjectModeProvider {
  override def getSystemId: ProjectSystemId = SbtProjectSystem.Id

  override def shouldShowEditorNotification(project: Project): Boolean =
    !SbtSettings.getInstance(project).getLinkedProjectsSettings.isEmpty

  override def loadAllLinkedProjects(project: Project): Unit =
    ExternalSystemUtil.refreshProjects(new ImportSpecBuilder(project, SbtProjectSystem.Id))
}