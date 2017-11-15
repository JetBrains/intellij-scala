package org.jetbrains.plugins.cbt.project

import java.io.File

import com.intellij.openapi.externalSystem.model.project._
import com.intellij.openapi.externalSystem.model.task.{ExternalSystemTaskId, ExternalSystemTaskNotificationEvent, ExternalSystemTaskNotificationListener}
import com.intellij.openapi.externalSystem.model.{DataNode, ExternalSystemException}
import com.intellij.openapi.externalSystem.service.project.ExternalSystemProjectResolver
import com.intellij.openapi.project.ProjectManager
import org.jetbrains.plugins.cbt.process.CbtProcess
import org.jetbrains.plugins.cbt.project.settings.CbtExecutionSettings
import org.jetbrains.plugins.cbt.project.template.CbtProjectImporter
import org.jetbrains.plugins.cbt.runner.CbtProcessListener

import scala.util.Failure

class CbtProjectResolver extends ExternalSystemProjectResolver[CbtExecutionSettings] {

  override def resolveProjectInfo(id: ExternalSystemTaskId,
                                  projectPath: String,
                                  isPreviewMode: Boolean,
                                  settings: CbtExecutionSettings,
                                  listener: ExternalSystemTaskNotificationListener): DataNode[ProjectData] = {
    val projectPath = settings.realProjectPath
    val root = new File(projectPath)
    val project =
      ProjectManager.getInstance.getOpenProjects
        .toSeq
        .find(_.getBaseDir.getCanonicalPath == projectPath)
        .get

    val cbtListener =
      new CbtProcessListener {
        override def onTextAvailable(text: String, stderr: Boolean): Unit =
          if (stderr)
            listener.onStatusChange(new ExternalSystemTaskNotificationEvent(id, text))

        override def onComplete(exitCode: Int): Unit = ()
      }

    CbtProcess.buildInfoXml(root, settings, cbtListener, project)
      .flatMap(CbtProjectImporter.importProject(_, settings))
      .recoverWith { case ex => Failure(new ExternalSystemException(ex)) }
      .get
  }

  override def cancelTask(taskId: ExternalSystemTaskId, listener: ExternalSystemTaskNotificationListener): Boolean = true
}

