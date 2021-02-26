package org.jetbrains.bsp.project

import com.intellij.openapi.externalSystem.importing.ImportSpecBuilder
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.externalSystem.service.project.UntrustedProjectModeProvider
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.project.Project
import org.jetbrains.bsp.BSP
import org.jetbrains.bsp.settings.BspSettings

class BspUntrustedModeProvider extends UntrustedProjectModeProvider {
  override def getSystemId: ProjectSystemId = BSP.ProjectSystemId

  override def shouldShowEditorNotification(project: Project): Boolean =
    !BspSettings.getInstance(project).getLinkedProjectsSettings.isEmpty

  override def loadAllLinkedProjects(project: Project): Unit =
    ExternalSystemUtil.refreshProjects(new ImportSpecBuilder(project, BSP.ProjectSystemId))
}