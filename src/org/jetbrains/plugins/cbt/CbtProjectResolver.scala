package org.jetbrains.plugins.cbt

import java.io.File

import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.project._
import com.intellij.openapi.externalSystem.model.task.{ExternalSystemTaskId, ExternalSystemTaskNotificationListener}
import com.intellij.openapi.externalSystem.service.project.ExternalSystemProjectResolver
import org.jetbrains.plugins.cbt.project.model.{CbtProjectConverter, CbtProjectInfo}
import org.jetbrains.plugins.cbt.project.settings.CbtExecutionSettings

  
class CbtProjectResolver extends ExternalSystemProjectResolver[CbtExecutionSettings] {

  override def resolveProjectInfo(id: ExternalSystemTaskId,
                                  projectPath: String,
                                  isPreviewMode: Boolean,
                                  settings: CbtExecutionSettings,
                                  listener: ExternalSystemTaskNotificationListener): DataNode[ProjectData] = {
    val projectPath = settings.realProjectPath
    val root = new File(projectPath)
    println("Cbt resolver called")

    val xml = CBT.buildInfoXml(root, settings.extraModules, Some(id, listener))
    println(xml.toString)
    val project = CbtProjectInfo(xml)
    val ideaProjectModel = CbtProjectConverter(project, settings)
    ideaProjectModel
  }

  override def cancelTask(taskId: ExternalSystemTaskId, listener: ExternalSystemTaskNotificationListener): Boolean = true
}

