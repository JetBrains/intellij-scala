package org.jetbrains.plugins.cbt

import java.io.File

import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.model.task.{ExternalSystemTaskId, ExternalSystemTaskNotificationListener}
import com.intellij.openapi.externalSystem.service.project.ExternalSystemProjectResolver
import org.jetbrains.plugins.cbt.project.settings.CbtExecutionSettings
import org.jetbrains.sbt.project.data.ProjectNode

class CbtProjectResolver extends ExternalSystemProjectResolver[CbtExecutionSettings] {
  override def resolveProjectInfo(id: ExternalSystemTaskId,
                                  projectPath: String,
                                  isPreviewMode: Boolean,
                                  settings: CbtExecutionSettings,
                                  listener: ExternalSystemTaskNotificationListener): DataNode[ProjectData] = {
    val root = {
      val file = new File(settings.realProjectPath)
      if (file.isDirectory) file.getPath else file.getParent
    }
    println("Cbt resolver called")

    val projectNode= new ProjectNode("", root, root)
    projectNode.toDataNode
  }

  override def cancelTask(taskId: ExternalSystemTaskId, listener: ExternalSystemTaskNotificationListener): Boolean = true
}
