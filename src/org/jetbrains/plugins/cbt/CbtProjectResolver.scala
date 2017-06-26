package org.jetbrains.plugins.cbt

import java.io.File

import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.project._
import com.intellij.openapi.externalSystem.model.task.{ExternalSystemTaskId, ExternalSystemTaskNotificationListener}
import com.intellij.openapi.externalSystem.service.project.ExternalSystemProjectResolver
import org.jetbrains.plugins.cbt.project.model.{Converter, ProjectInfo}
import org.jetbrains.plugins.cbt.project.settings.CbtExecutionSettings

import scala.xml.XML

class CbtProjectResolver extends ExternalSystemProjectResolver[CbtExecutionSettings] {

  override def resolveProjectInfo(id: ExternalSystemTaskId,
                                  projectPath: String,
                                  isPreviewMode: Boolean,
                                  settings: CbtExecutionSettings,
                                  listener: ExternalSystemTaskNotificationListener): DataNode[ProjectData] = {
    val projectPath = settings.realProjectPath
    val root = new File(projectPath)
    println("Cbt resolver called")
    val xml = XML.loadString(CBT.runAction(Seq("buildInfoXml"), root, Some(id, listener)))
    println(xml.toString)
    val project = ProjectInfo(xml)
    val ideaProjectModel = Converter(project, settings)
    ideaProjectModel
  }

  override def cancelTask(taskId: ExternalSystemTaskId, listener: ExternalSystemTaskNotificationListener): Boolean = true
}

