package org.jetbrains.bsp.project.resolver

import java.io.{File, PrintWriter, StringWriter}
import java.util.Collections
import java.util.concurrent.CompletableFuture

import ch.epfl.scala.bsp4j._
import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.project._
import com.intellij.openapi.externalSystem.model.task.{ExternalSystemTaskId, ExternalSystemTaskNotificationListener}
import com.intellij.openapi.externalSystem.service.project.ExternalSystemProjectResolver
import org.jetbrains.bsp.BspTaskCancelled
import org.jetbrains.bsp.BspUtil._
import org.jetbrains.bsp.project.resolver.BspProjectResolver._
import org.jetbrains.bsp.project.resolver.BspResolverDescriptors._
import org.jetbrains.bsp.project.resolver.BspResolverLogic._
import org.jetbrains.bsp.protocol.session.BspSession.{BspServer, NotificationCallback, ProcessLogger}
import org.jetbrains.bsp.protocol.{BspCommunication, BspJob, BspNotifications}
import org.jetbrains.bsp.settings.BspExecutionSettings
import org.jetbrains.plugins.scala.build.ExternalSystemNotificationReporter

import scala.annotation.tailrec
import scala.collection.JavaConverters._
import scala.concurrent.duration._
import scala.concurrent.{Await, TimeoutException}
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}

class BspProjectResolver extends ExternalSystemProjectResolver[BspExecutionSettings] {

  private var importState: ImportState = Inactive

  override def resolveProjectInfo(id: ExternalSystemTaskId,
                                  workspaceCreationPath: String,
                                  isPreviewMode: Boolean,
                                  executionSettings: BspExecutionSettings,
                                  listener: ExternalSystemTaskNotificationListener): DataNode[ProjectData] = {

    val reporter = new ExternalSystemNotificationReporter(workspaceCreationPath, id, listener)
    val workspaceCreationFile = new File(workspaceCreationPath)
    val workspace =
      if (workspaceCreationFile.isDirectory) workspaceCreationFile
      else workspaceCreationFile.getParentFile

    val moduleFilesDirectoryPath = new File(workspace, ".idea/modules").getAbsolutePath

    def requests(implicit server: BspServer, capabilities: BuildServerCapabilities): CompletableFuture[DataNode[ProjectData]] = {
      val targetsRequest = server.workspaceBuildTargets()

      val projectNodeFuture: CompletableFuture[DataNode[ProjectData]] =
        targetsRequest.thenCompose { targetsResponse =>
          val targets = targetsResponse.getTargets.asScala.toList
          val td = targetData(targets, isPreviewMode)
          td.thenApply { data =>

            val sources = data.sources.map(_.getItems.asScala).getOrElse {
              reporter.warning("request failed: buildTarget/sources", None)
              List.empty[SourcesItem]
            }

            val depSources = data.dependencySources.map(_.getItems.asScala).getOrElse {
              reporter.warning("request failed: buildTarget/dependencySources", None)
              List.empty[DependencySourcesItem]
            }
            val resources = data.resources.map(_.getItems.asScala).getOrElse {
              reporter.warning("request failed: buildTarget/resources", None)
              List.empty[ResourcesItem]
            }
            val scalacOptions = data.scalacOptions.map(_.getItems.asScala).getOrElse {
              reporter.warning("request failed: buildTarget/scalacOptions", None)
              List.empty[ScalacOptionsItem]
            }

            val descriptions = calculateModuleDescriptions(
              targets, scalacOptions, sources, resources, depSources
            )
            projectNode(workspace.getCanonicalPath, moduleFilesDirectoryPath, descriptions)
          }
        }

      projectNodeFuture
    }

    // TODO reuse existing connection if available. https://youtrack.jetbrains.com/issue/SCL-14847
    val communication = BspCommunication.forWorkspace(new File(workspaceCreationPath))

    val notifications: NotificationCallback = {
      case BspNotifications.LogMessage(params) =>
        // TODO use params.id for tree structure
        reporter.log(params.getMessage)
      case _ =>
    }

    val processLogger: ProcessLogger = { msg =>
      listener.onTaskOutput(id, msg, true)
    }

    val projectJob =
      communication.run(requests(_,_), notifications, reporter, processLogger)

    reporter.start()
    reporter.log("BSP import started")

    importState = Active(communication)
    val result = waitForProjectCancelable(projectJob)
    importState = Inactive

    reporter.log("BSP import completed")

    result match {
      case Failure(BspTaskCancelled) =>
        listener.onCancel(id)
        null
      case Failure(err: Exception) =>
        val writer = new PrintWriter(new StringWriter())
        err.printStackTrace(writer)
        listener.onTaskOutput(id, writer.toString, false)
        listener.onFailure(id, err)
        throw err
      case Success(data) =>
        listener.onSuccess(id)
        data
    }
  }

  @tailrec private def waitForProjectCancelable[T](projectJob: BspJob[DataNode[ProjectData]]): Try[DataNode[ProjectData]] =

    importState match {
      case Active(_) =>
        var retry = false

        val res = try {
          val res = Await.result(projectJob.future, 300.millis)
          Success(res)
        }
        catch {
          case to: TimeoutException =>
            retry = true // hack around tail call optimization not working in catch
            Failure(to)
          case NonFatal(x) => Failure(x)
        }

        if (retry) waitForProjectCancelable(projectJob)
        else res

      case Inactive =>
        projectJob.cancel()
        Failure(BspTaskCancelled)
    }

  override def cancelTask(taskId: ExternalSystemTaskId,
                          listener: ExternalSystemTaskNotificationListener): Boolean =
    importState match {
      case Active(session) =>
        listener.beforeCancel(taskId)
        importState = Inactive
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

  private[resolver] def targetData(targets: List[BuildTarget], isPreview: Boolean)
                                  (implicit bsp: BspServer, capabilities: BuildServerCapabilities):
  CompletableFuture[TargetData] =
    if (isPreview) {
      val emptySources = Success(new SourcesResult(Collections.emptyList()))
      val emptyResources = Success(new ResourcesResult(Collections.emptyList()))
      val emptyDepSources = Success(new DependencySourcesResult(Collections.emptyList()))
      val emptyScalacOpts = Success(new ScalacOptionsResult(Collections.emptyList()))

      CompletableFuture.completedFuture(TargetData(emptySources, emptyDepSources, emptyResources, emptyScalacOpts))
    } else {
      val targetIds = targets.map(_.getId).asJava

      val sourcesParams = new SourcesParams(targetIds)
      val sources = bsp.buildTargetSources(sourcesParams).catchBspErrors

      val depSources = if (isDependencySourcesProvider) {
        val depSourcesParams = new DependencySourcesParams(targetIds)
        bsp.buildTargetDependencySources(depSourcesParams).catchBspErrors
      } else {
        val emptyResult = new DependencySourcesResult(Collections.emptyList())
        CompletableFuture.completedFuture[Try[DependencySourcesResult]](Success(emptyResult))
      }

      val resources = if (isResourcesProvider) {
        val resourcesParams = new ResourcesParams(targetIds)
        bsp.buildTargetResources(resourcesParams).catchBspErrors
      } else {
        val emptyResult = new ResourcesResult(Collections.emptyList())
        CompletableFuture.completedFuture[Try[ResourcesResult]](Success(emptyResult))
      }

      val scalaTargetIds = targets
        .filter(_.getLanguageIds.contains("scala"))
        .map(_.getId).asJava
      val scalacOptionsParams = new ScalacOptionsParams(scalaTargetIds)
      val scalacOptions = bsp.buildTargetScalacOptions(scalacOptionsParams).catchBspErrors

      CompletableFuture
        .allOf(sources, depSources, resources, scalacOptions)
        .thenApply(_ => TargetData(sources.get, depSources.get, resources.get, scalacOptions.get))
    }

  private def isDependencySourcesProvider(implicit capabilities: BuildServerCapabilities) =
    Option(capabilities.getDependencySourcesProvider).exists(_.booleanValue())

  private def isResourcesProvider(implicit capabilities: BuildServerCapabilities) =
    Option(capabilities.getResourcesProvider).exists(_.booleanValue())

}
