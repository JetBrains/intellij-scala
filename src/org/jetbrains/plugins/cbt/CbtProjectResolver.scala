package org.jetbrains.plugins.cbt

import java.io.File

import com.intellij.openapi.externalSystem.model.{DataNode, ExternalSystemException}
import com.intellij.openapi.externalSystem.model.project._
import com.intellij.openapi.externalSystem.model.task.{ExternalSystemTaskId, ExternalSystemTaskNotificationListener}
import com.intellij.openapi.externalSystem.service.project.ExternalSystemProjectResolver
import org.jetbrains.plugins.cbt.project.CbtProjectSystem
import org.jetbrains.plugins.cbt.project.model.{CbtProjectConverter, CbtProjectInfo}
import org.jetbrains.plugins.cbt.project.settings.CbtExecutionSettings
import org.jetbrains.plugins.cbt.project.structure.CbtProjectImporingException

import scala.util.Failure


class CbtProjectResolver extends ExternalSystemProjectResolver[CbtExecutionSettings] {

  override def resolveProjectInfo(id: ExternalSystemTaskId,
                                  projectPath: String,
                                  isPreviewMode: Boolean,
                                  settings: CbtExecutionSettings,
                                  listener: ExternalSystemTaskNotificationListener): DataNode[ProjectData] = {
    val projectPath = settings.realProjectPath
    val root = new File(projectPath)
    println("Cbt resolver called")

    val xml = CBT.buildInfoXml(root, settings, Some(id, listener))
    println(xml.toString)
    xml.map(CbtProjectInfo(_))
      .flatMap(CbtProjectConverter(_, settings))
      .get
  }

  override def cancelTask(taskId: ExternalSystemTaskId, listener: ExternalSystemTaskNotificationListener): Boolean = true
}

