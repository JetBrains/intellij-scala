package org.jetbrains.bsp

import java.io.File

import ch.epfl.scala.bsp.endpoints
import ch.epfl.scala.bsp.schema.WorkspaceBuildTargetsRequest
import com.intellij.openapi.externalSystem.model.project.{ModuleData, ProjectData}
import com.intellij.openapi.externalSystem.model.task.{ExternalSystemTaskId, ExternalSystemTaskNotificationEvent, ExternalSystemTaskNotificationListener}
import com.intellij.openapi.externalSystem.model.{DataNode, ProjectKeys}
import com.intellij.openapi.externalSystem.service.project.ExternalSystemProjectResolver
import com.intellij.openapi.module.StdModuleTypes
import monix.execution.{Cancelable, ExecutionModel, Scheduler}
import monix.reactive.Consumer
import org.jetbrains.bsp.BspProjectResolver.ActiveImport
import org.jetbrains.ide.PooledThreadExecutor
import org.langmeta.jsonrpc.BaseProtocolMessage
import org.langmeta.lsp.LanguageClient

import scala.concurrent.Await
import scala.concurrent.duration.Duration

class BspProjectResolver extends ExternalSystemProjectResolver[BspExecutionSettings] {

  private var activeImport: Option[ActiveImport] = None

  override def resolveProjectInfo(id: ExternalSystemTaskId,
                                  projectPath: String,
                                  isPreviewMode: Boolean,
                                  settings: BspExecutionSettings,
                                  listener: ExternalSystemTaskNotificationListener): DataNode[ProjectData] = {

    val projectRoot = new File(projectPath)
    val moduleFilesDirectoryPath = new File(projectPath, ".idea/modules").getAbsolutePath

    implicit val scheduler: Scheduler = Scheduler(PooledThreadExecutor.INSTANCE, ExecutionModel.AlwaysAsyncExecution)

    def statusUpdate(msg: String): Unit = {
      val ev = new ExternalSystemTaskNotificationEvent(id, msg)
      listener.onStatusChange(ev)
    }

    val msgConsumer = Consumer.foreach[BaseProtocolMessage] { msg =>
      val text = new String(msg.content)
      listener.onTaskOutput(id, text, true)
    }

    def targetsReq(implicit client: LanguageClient) =
      endpoints.Workspace.buildTargets.request(WorkspaceBuildTargetsRequest())

    val projectTask = for {
      session <- BspCommunication.prepareSession(projectRoot)
      _ = statusUpdate("session prepared")
      msgs = session.messages.consumeWith(msgConsumer) // TODO cancel this on finish?
      targetsResponse <- session.run(targetsReq(_))
    } yield {

      statusUpdate("targets fetched")

      // TODO handle error response
      val modules = for {
        target <- targetsResponse.right.get.targets
      } yield {
        val uri = target.id.get.uri
        val name = target.displayName

        val data = new ModuleData(uri, bsp.ProjectSystemId, StdModuleTypes.JAVA.getId, name, moduleFilesDirectoryPath, projectPath)
        data.setInheritProjectCompileOutputPath(false)
        data
      }

      val projectData = new ProjectData(bsp.ProjectSystemId, projectRoot.getName, projectPath, projectPath)
      val projectNode = new DataNode[ProjectData](ProjectKeys.PROJECT, projectData, null)

      modules.foreach { data =>
        val moduleNode = new DataNode[ModuleData](ProjectKeys.MODULE, data, projectNode)
        projectNode.addChild(moduleNode)
      }

      projectNode
    }

    statusUpdate("starting task")

    val running = projectTask.runAsync
    activeImport = Some(ActiveImport(running))
    val result = Await.result(running, Duration.Inf)
    activeImport = None

    statusUpdate("finished task")

    result
  }

  override def cancelTask(taskId: ExternalSystemTaskId, listener: ExternalSystemTaskNotificationListener): Boolean =
    activeImport match {
      case Some(ActiveImport(running)) =>
        listener.beforeCancel(taskId)
        running.cancel()
        activeImport = None
        listener.onCancel(taskId)
        true // TODO this is a lie, cancel doesn't just cancel the thread
      case None =>
        false
    }

}

object BspProjectResolver {
  private case class ActiveImport(importCancelable: Cancelable)
}