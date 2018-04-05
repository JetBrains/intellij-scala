package org.jetbrains.bsp

import java.io.File
import java.util.Locale

import ch.epfl.scala.bsp.endpoints
import ch.epfl.scala.bsp.schema.WorkspaceBuildTargetsRequest
import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.model.task.{ExternalSystemTaskId, ExternalSystemTaskNotificationListener}
import com.intellij.openapi.externalSystem.service.project.ExternalSystemProjectResolver
import com.intellij.openapi.module.StdModuleTypes
import monix.execution.{Cancelable, ExecutionModel, Scheduler}
import org.jetbrains.bsp.BspProjectResolver.ActiveImport
import org.jetbrains.ide.PooledThreadExecutor
import org.jetbrains.sbt.project.data.{ModuleNode, ProjectNode}
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

    val initSession = BspCommunication.initialize(projectRoot)

    def targetsReq(implicit client: LanguageClient) =
      endpoints.Workspace.buildTargets.request(WorkspaceBuildTargetsRequest())

    val projectTask = for {
      session <- initSession
      targets <- targetsReq(session.client)
    } yield {
      // TODO handle error response
      val modules = for {
        target <- targets.right.get.targets
      } yield {
        val uri = target.id.get.uri
        val name = target.displayName

        val node = new ModuleNode(StdModuleTypes.JAVA.getId, uri, name, moduleFilesDirectoryPath, projectPath)
        node.setInheritProjectCompileOutputPath(false)
        node
      }

      val projectNode = new ProjectNode(projectRoot.getName, projectPath, projectPath)
      projectNode.addAll(modules)

      projectNode.toDataNode
    }

    val running = projectTask.runAsync
    activeImport = Some(ActiveImport(running))
    val result = Await.result(running, Duration.Inf)

    activeImport = None

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


  /**
    * This implementation is the same as in sbt.Project.normalizeModuleId to avoid inconsistencies in the import process.
    * Normalize a String so that it is suitable for use as a dependency management module identifier.
    * This is a best effort implementation, since valid characters are not documented or consistent.    *
    */
  private def normalizeModuleId(s: String) =
    s.toLowerCase(Locale.ENGLISH)
      .replaceAll("""\W+""", "-")
}

object BspProjectResolver {
  private case class ActiveImport(importCancelable: Cancelable)
}