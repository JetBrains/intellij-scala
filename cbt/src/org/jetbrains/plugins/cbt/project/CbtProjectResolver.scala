package org.jetbrains.plugins.cbt.project

import java.io.File

import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.project._
import com.intellij.openapi.externalSystem.model.task.{ExternalSystemTaskId, ExternalSystemTaskNotificationListener}
import com.intellij.openapi.externalSystem.service.project.ExternalSystemProjectResolver
import com.intellij.openapi.project.ProjectManager
import org.jetbrains.plugins.cbt.process.CbtProcess
import org.jetbrains.plugins.cbt.project.settings.{CbtExecutionSettings, CbtSystemSettings}
import org.jetbrains.plugins.cbt.project.template.CbtProjectImporter

class CbtProjectResolver extends ExternalSystemProjectResolver[CbtExecutionSettings] {

  override def resolveProjectInfo(id: ExternalSystemTaskId,
                                  projectPath: String,
                                  isPreviewMode: Boolean,
                                  settings: CbtExecutionSettings,
                                  listener: ExternalSystemTaskNotificationListener): DataNode[ProjectData] = {
    val projectPath = settings.realProjectPath
    val root = new File(projectPath)
    val projectOpt =
      ProjectManager.getInstance.getOpenProjects
        .toSeq
        .find(_.getBaseDir.getCanonicalPath == projectPath)
    val xml = CbtProcess.buildInfoXml(root, settings, projectOpt, Some(id, listener))
    xml.flatMap(CbtProjectImporter.importProject(_, settings)).get
  }

  override def cancelTask(taskId: ExternalSystemTaskId, listener: ExternalSystemTaskNotificationListener): Boolean = true
}

