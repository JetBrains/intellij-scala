package org.jetbrains.bsp.project.resolver

import java.io.File
import java.util.Collections
import java.util.concurrent.CompletableFuture

import ch.epfl.scala.bsp4j._
import com.intellij.build.events.impl.SuccessResultImpl
import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.project._
import com.intellij.openapi.externalSystem.model.task.{ExternalSystemTaskId, ExternalSystemTaskNotificationListener}
import com.intellij.openapi.externalSystem.service.project.ExternalSystemProjectResolver
import com.intellij.openapi.projectRoots.{JavaSdk, ProjectJdkTable}
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import org.jetbrains.bsp.BspUtil._
import org.jetbrains.bsp.project.resolver.BspProjectResolver._
import org.jetbrains.bsp.project.resolver.BspResolverDescriptors._
import org.jetbrains.bsp.project.resolver.BspResolverLogic._
import org.jetbrains.bsp.protocol.session.BspSession.{BspServer, NotificationCallback, ProcessLogger}
import org.jetbrains.bsp.protocol.{BspCommunication, BspJob, BspNotifications}
import org.jetbrains.bsp.settings.BspExecutionSettings
import org.jetbrains.bsp.{BspErrorMessage, BspTaskCancelled}
import org.jetbrains.plugins.scala.build.BuildMessages.EventId
import org.jetbrains.plugins.scala.build.{BuildMessages, BuildTaskReporter, ExternalSystemNotificationReporter}
import org.jetbrains.plugins.scala.buildinfo.BuildInfo
import org.jetbrains.sbt.SbtUtil
import org.jetbrains.sbt.project.structure.SbtStructureDump
import org.jetbrains.sbt.project.{SbtExternalSystemManager, SbtProjectImportProvider}

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
      val structureEventId = BuildMessages.randomEventId
      reporter.startTask(structureEventId, None,"resolving BSP build structure")
      val targetsEventId = BuildMessages.randomEventId
      reporter.startTask(targetsEventId, Some(structureEventId), "build targets")
      val targetsRequest = server.workspaceBuildTargets()

      val projectNodeFuture: CompletableFuture[DataNode[ProjectData]] =
        targetsRequest.thenCompose { targetsResponse =>
          reporter.finishTask(targetsEventId, "build targets", new SuccessResultImpl())

          val targets = targetsResponse.getTargets.asScala.toList
          val td = targetData(targets, isPreviewMode, reporter, structureEventId)

          td.thenApply[DataNode[ProjectData]] { data =>
            val sources = data.sources.map(_.getItems.asScala).getOrElse {List.empty[SourcesItem]}
            val depSources = data.dependencySources.map(_.getItems.asScala).getOrElse {List.empty[DependencySourcesItem]}
            val resources = data.resources.map(_.getItems.asScala).getOrElse {List.empty[ResourcesItem]}
            val scalacOptions = data.scalacOptions.map(_.getItems.asScala).getOrElse {List.empty[ScalacOptionsItem]}

            val descriptions = calculateModuleDescriptions(
              targets, scalacOptions, sources, resources, depSources
            )
            projectNode(workspace.getCanonicalPath, moduleFilesDirectoryPath, descriptions)
          }
            .reportFinished(reporter, structureEventId,
              "BSP build structure",
              "resolving BSP build structure failed")
        }

      projectNodeFuture
    }

    val communication = BspCommunication.forWorkspace(new File(workspaceCreationPath))

    val notifications: NotificationCallback = {
      case BspNotifications.LogMessage(params) =>
        // TODO use params.id for tree structure?
        reporter.log(params.getMessage)
      case _ =>
    }

    val processLogger: ProcessLogger = { msg =>
      listener.onTaskOutput(id, msg, true)
    }

    reporter.start()

    importState = Active(communication)

    // special handling for sbt projects: run bloopInstall first
    // TODO support other bloop-enabled build tools as well
    val vfile = LocalFileSystem.getInstance().findFileByIoFile(workspaceCreationFile)
    val sbtMessages = if (SbtProjectImportProvider.canImport(vfile)) {
      runBloopInstall(workspaceCreationFile, reporter)
    } else Success(BuildMessages.empty.status(BuildMessages.OK))

    val result = sbtMessages match {
      case Success(messages) if messages.status == BuildMessages.OK =>
        val projectJob = communication.run(requests(_,_), notifications, reporter, processLogger)
        waitForProjectCancelable(projectJob) match {
          case Failure(BspTaskCancelled) =>
            reporter.finishCanceled()
            null
          case Failure(err: Exception) =>
            reporter.finishWithFailure(err)
            throw err
          case Success(data) =>
            reporter.finish(messages)
            data

        }
      case Success(messages) =>
        reporter.finish(messages)
        throw BspErrorMessage("import failed")
      case Failure(x) =>
        reporter.finishWithFailure(x)
        throw x
    }

    importState = Inactive

    result

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

  private[resolver] def targetData(targets: List[BuildTarget], isPreview: Boolean, reporter: BuildTaskReporter, parentId: EventId)
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

      val sourcesEventId = BuildMessages.randomEventId
      val sourcesParams = new SourcesParams(targetIds)
      val message = "sources"
      reporter.startTask(sourcesEventId, Some(parentId), message)
      val sources = bsp.buildTargetSources(sourcesParams)
        .catchBspErrors
        .reportFinished(reporter, sourcesEventId, message, "request failed: buildTarget/sources")

      val depSources = if (isDependencySourcesProvider) {
        val eventId = BuildMessages.randomEventId
        val depSourcesParams = new DependencySourcesParams(targetIds)
        val message = "dependency sources"
        reporter.startTask(eventId, Some(parentId), message)
        bsp.buildTargetDependencySources(depSourcesParams)
          .catchBspErrors
          .reportFinished(reporter, eventId, message, "request failed: buildTarget/dependencySources")
      } else {
        val emptyResult = new DependencySourcesResult(Collections.emptyList())
        CompletableFuture.completedFuture[Try[DependencySourcesResult]](Success(emptyResult))
      }

      val resources = if (isResourcesProvider) {
        val eventId = BuildMessages.randomEventId
        val resourcesParams = new ResourcesParams(targetIds)
        val message = "resources"
        reporter.startTask(eventId, Some(parentId), message)
        bsp.buildTargetResources(resourcesParams)
          .catchBspErrors
          .reportFinished(reporter, eventId, message, "request failed: buildTarget/resources")
      } else {
        val emptyResult = new ResourcesResult(Collections.emptyList())
        CompletableFuture.completedFuture[Try[ResourcesResult]](Success(emptyResult))
      }

      val scalaTargetIds = targets
        .filter(_.getLanguageIds.contains("scala"))
        .map(_.getId).asJava

      val scalacOptions = if (!scalaTargetIds.isEmpty) {
        val eventId = BuildMessages.randomEventId
        val message = "scalac options"
        reporter.startTask(eventId, Some(parentId), message)

        val scalacOptionsParams = new ScalacOptionsParams(scalaTargetIds)
        bsp.buildTargetScalacOptions(scalacOptionsParams)
          .catchBspErrors
          .reportFinished(reporter, eventId, message, "request failed: buildTarget/scalacOptions")

      } else {
        val emptyResult = new ScalacOptionsResult(Collections.emptyList())
        CompletableFuture.completedFuture[Try[ScalacOptionsResult]](Success(emptyResult))
      }


      CompletableFuture
        .allOf(sources, depSources, resources, scalacOptions)
        .thenApply(_ => TargetData(sources.get, depSources.get, resources.get, scalacOptions.get))
    }


  private def isDependencySourcesProvider(implicit capabilities: BuildServerCapabilities) =
    Option(capabilities.getDependencySourcesProvider).exists(_.booleanValue())

  private def isResourcesProvider(implicit capabilities: BuildServerCapabilities) =
    Option(capabilities.getResourcesProvider).exists(_.booleanValue())

  private def runBloopInstall(baseDir: File, reporter: BuildTaskReporter) = {

    val jdkType = JavaSdk.getInstance()
    val jdk = ProjectJdkTable.getInstance().findMostRecentSdkOfType(jdkType)
    val jdkExe = new File(jdkType.getVMExecutablePath(jdk)) // TODO error when none, offer jdk config
    val jdkHome = Option(jdk.getHomePath).map(new File(_))
    val sbtLauncher = SbtUtil.getDefaultLauncher

    val injectedPlugins = s"""addSbtPlugin("ch.epfl.scala" % "sbt-bloop" % "${BuildInfo.bloopVersion}")"""
    val pluginFile = FileUtil.createTempFile("idea",".sbt", true)
    val pluginFilePath = SbtUtil.normalizePath(pluginFile)
    FileUtil.writeToFile(pluginFile, injectedPlugins)

    val injectedSettings = """bloopExportJarClassifiers in Global := Some(Set("sources"))"""
    val settingsFile = FileUtil.createTempFile(baseDir, "idea-bloop", ".sbt", true)
    FileUtil.writeToFile(settingsFile, injectedSettings)

    val sbtCommandArgs = "early(addPluginSbtFile=\"\"\"" + pluginFilePath + "\"\"\")"
    val sbtCommands = "bloopInstall"

    try {
      val dumper = new SbtStructureDump()
      dumper.runSbt(
        baseDir, jdkExe,
        SbtExternalSystemManager.getVmOptions(Seq.empty, jdkHome),
        Map.empty, sbtLauncher, sbtCommandArgs, sbtCommands,
        reporter,
        "creating Bloop configuration from sbt",
      )
    } finally {
      settingsFile.delete()
    }
  }
}
