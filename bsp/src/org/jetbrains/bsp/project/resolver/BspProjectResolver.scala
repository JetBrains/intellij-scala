package org.jetbrains.bsp.project.resolver

import java.io.File
import java.util.Collections
import java.util.concurrent.CompletableFuture

import ch.epfl.scala.bsp4j._
import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.project._
import com.intellij.openapi.externalSystem.model.task.{ExternalSystemTaskId, ExternalSystemTaskNotificationEvent, ExternalSystemTaskNotificationListener}
import com.intellij.openapi.externalSystem.service.project.ExternalSystemProjectResolver
import org.jetbrains.bsp.BspUtil._
import org.jetbrains.bsp.project.resolver.BspProjectResolver._
import org.jetbrains.bsp.project.resolver.BspResolverDescriptors._
import org.jetbrains.bsp.project.resolver.BspResolverLogic._
import org.jetbrains.bsp.protocol.BspSession.{BspServer, NotificationCallback, ProcessLogger}
import org.jetbrains.bsp.protocol.{BspCommunication, BspJob, BspNotifications}
import org.jetbrains.bsp.settings.BspExecutionSettings
import org.jetbrains.bsp.{BspError, BspTaskCancelled}

import scala.annotation.tailrec
import scala.collection.JavaConverters._
import scala.concurrent.duration._
import scala.concurrent.{Await, TimeoutException}

class BspProjectResolver extends ExternalSystemProjectResolver[BspExecutionSettings] {

  private var importState: ImportState = Inactive

  override def resolveProjectInfo(id: ExternalSystemTaskId,
                                  projectRootPath: String,
                                  isPreviewMode: Boolean,
                                  executionSettings: BspExecutionSettings,
                                  listener: ExternalSystemTaskNotificationListener): DataNode[ProjectData] = {

    val moduleFilesDirectoryPath = new File(projectRootPath, ".idea/modules").getAbsolutePath

    def statusUpdate(msg: String): Unit = {
      val ev = new ExternalSystemTaskNotificationEvent(id, msg)
      listener.onStatusChange(ev)
    }

    def requests(implicit server: BspServer): CompletableFuture[Either[BspError, DataNode[ProjectData]]] = {
      val targetsRequest = server.workspaceBuildTargets()

      val projectNodeFuture: CompletableFuture[Either[BspError,DataNode[ProjectData]]] =
        targetsRequest.thenCompose { targetsResponse =>
          val targets = targetsResponse.getTargets.asScala
          val targetIds = targets.map(_.getId).toList
          val td = targetData(targetIds, isPreviewMode)
          td.thenApply { data =>
            for {
              sources <- data.sources
              depSources <- data.dependencySources // TODO not required for project, should be warning
              scalacOptions <- data.scalacOptions // TODO not required for non-scala modules
            } yield {
              val descriptions = calculateModuleDescriptions(
                targets,
                scalacOptions.getItems.asScala,
                sources.getItems.asScala,
                depSources.getItems.asScala
              )
              projectNode(projectRootPath, moduleFilesDirectoryPath, descriptions)
            }
          }
        }

      projectNodeFuture
    }

    // TODO reuse existing connection if available. https://youtrack.jetbrains.com/issue/SCL-14847
    val communication = BspCommunication.forBaseDir(projectRootPath, executionSettings)

    val notifications: NotificationCallback = {
      case BspNotifications.LogMessage(params) =>
        // TODO use params.id for tree structure
        statusUpdate(params.getMessage)
      case _ =>
    }

    val processLogger: ProcessLogger = { msg =>
      listener.onTaskOutput(id, msg, true)
    }

    val projectJob =
      communication.run(requests(_), notifications, processLogger)

    statusUpdate("starting task") // TODO remove in favor of build toolwindow nodes

    importState = Active(communication)
    val result = waitForProjectCancelable(projectJob)
    communication.closeSession()
    importState = Inactive

    statusUpdate("finished task") // TODO remove in favor of build toolwindow nodes

    result match {
      case Left(BspTaskCancelled) => null
      case Left(err) => throw err
      case Right(data) => data
    }
  }

  @tailrec private def waitForProjectCancelable[T](projectJob: BspJob[Either[BspError, DataNode[ProjectData]]]): Either[BspError, DataNode[ProjectData]] =
    importState match {
      case Active(_) =>
        try { Await.result(projectJob.future, 300.millis) }
        catch {
          case _: TimeoutException => waitForProjectCancelable(projectJob)
        }
      case Inactive =>
        projectJob.cancel()
        Left(BspTaskCancelled)
    }

  override def cancelTask(taskId: ExternalSystemTaskId,
                          listener: ExternalSystemTaskNotificationListener): Boolean =
    importState match {
      case Active(session) =>
        listener.beforeCancel(taskId)
        importState = Inactive
        session.closeSession()
        listener.onCancel(taskId)
        true
      case Inactive =>
        false
    }

}

object BspProjectResolver {

  private sealed abstract class ImportState
  private case class Active(communication: BspCommunication) extends ImportState
  private case object Inactive extends ImportState

  private[resolver] def targetData(targetIds: List[BuildTargetIdentifier], isPreview: Boolean)(implicit bsp: BspServer):
  CompletableFuture[TargetData] =
    if (isPreview) {
      val emptySources = Right[BspError,SourcesResult](new SourcesResult(Collections.emptyList()))
      val emptyDS = Right[BspError,DependencySourcesResult](new DependencySourcesResult(Collections.emptyList()))
      val emptySO = Right[BspError,ScalacOptionsResult](new ScalacOptionsResult(Collections.emptyList()))
      CompletableFuture.completedFuture(TargetData(emptySources, emptyDS, emptySO))
    } else {
      val targets = targetIds.asJava
      val sourcesParams = new SourcesParams(targets)
      val sources = bsp.buildTargetSources(sourcesParams).catchBspErrors
      val depSourcesParams = new DependencySourcesParams(targets)
      val depSources = bsp.buildTargetDependencySources(depSourcesParams).catchBspErrors
      val scalacOptionsParams = new ScalacOptionsParams(targets)
      val scalacOptions = bsp.buildTargetScalacOptions(scalacOptionsParams).catchBspErrors

      sources
        .thenCompose { src =>
          depSources.thenCompose { ds =>
            scalacOptions.thenApply { so =>
              TargetData(src,ds,so)
            }
          }
        }
    }
}
