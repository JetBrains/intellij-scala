package org.jetbrains.bsp.project.importing

import ch.epfl.scala.bsp4j._
import com.intellij.build.events.impl.SuccessResultImpl
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.externalSystem.model.project._
import com.intellij.openapi.externalSystem.model.task.{ExternalSystemTaskId, ExternalSystemTaskNotificationListener}
import com.intellij.openapi.externalSystem.model.{DataNode, ExternalSystemException}
import com.intellij.openapi.externalSystem.service.project.ExternalSystemProjectResolver
import org.jetbrains.bsp.BspUtil.{bloopConfigDir, _}
import org.jetbrains.bsp.project.importing.BspProjectResolver._
import org.jetbrains.bsp.project.importing.BspResolverDescriptors._
import org.jetbrains.bsp.project.importing.BspResolverLogic._
import org.jetbrains.bsp.project.importing.preimport.{BloopPreImporter, PreImporter}
import org.jetbrains.bsp.protocol.session.Bsp4JJobFailure
import org.jetbrains.bsp.protocol.session.BspSession.{BspServer, BuildServerInfo, NotificationAggregator}
import org.jetbrains.bsp.protocol.{BspCommunication, BspConnectionConfig, BspJob, BspNotifications}
import org.jetbrains.bsp.settings.{BspExecutionSettings, BspProjectSettings}
import org.jetbrains.bsp.{BspBundle, BspErrorMessage, BspJdkUtil, BspNoJdkConfiguredError, BspTaskCancelled, BspUtil}
import org.jetbrains.plugins.scala.build.BuildMessages.EventId
import org.jetbrains.plugins.scala.build.{BuildMessages, BuildReporter, ExternalSystemNotificationReporter}

import java.io.File
import java.util.Collections
import java.util.concurrent.CompletableFuture
import scala.annotation.tailrec
import scala.concurrent.duration._
import scala.concurrent.{Await, TimeoutException}
import scala.jdk.CollectionConverters._
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}

class BspProjectResolver extends ExternalSystemProjectResolver[BspExecutionSettings] {

  @volatile private var importState: ImportState = Inactive

  override def resolveProjectInfo(id: ExternalSystemTaskId,
                                  workspaceCreationPath: String,
                                  isPreviewMode: Boolean,
                                  executionSettings: BspExecutionSettings,
                                  listener: ExternalSystemTaskNotificationListener): DataNode[ProjectData] = {

    implicit val reporter: BuildReporter = new ExternalSystemNotificationReporter(workspaceCreationPath, id, listener)
    val workspaceCreationFile = new File(workspaceCreationPath)
    val workspace =
      if (workspaceCreationFile.isDirectory || !workspaceCreationFile.exists) workspaceCreationFile
      else workspaceCreationFile.getParentFile

    importState = Active

    reporter.start()

    val result = if (isPreviewMode) {
      val modules = ProjectModules(Nil, Nil)
      reporter.finish(BuildMessages.empty.status(BuildMessages.OK))
      projectNode(workspace, modules, rootExclusions(workspace), "dummy-display-name")
    } else {
      runImport(workspace, executionSettings)
    }

    importState = Inactive

    result

  }

  private def requests(workspace: File)
                      (implicit server: BspServer, serverInfo: BuildServerInfo, reporter: BuildReporter)
  : CompletableFuture[DataNode[ProjectData]] = {

    val structureEventId = BuildMessages.randomEventId
    reporter.startTask(structureEventId, None, BspBundle.message("bsp.resolver.resolving.build.structure"))
    val reloadEventId = BuildMessages.randomEventId
    val reloadRequest = {
      if (serverInfo.capabilities.getCanReload) {
        reporter.startTask(reloadEventId, Some(structureEventId), BspBundle.message("bsp.resolver.build.reloading"))
        server.workspaceReload()
      }
      else CompletableFuture.completedFuture(new Object)
    }
    val targetsEventId = BuildMessages.randomEventId
    val targetsRequest = reloadRequest.thenCompose { _ =>
      reporter.finishTask(reloadEventId, BspBundle.message("bsp.resolver.build.reloading"), new SuccessResultImpl())
      reporter.startTask(targetsEventId, Some(structureEventId), BspBundle.message("bsp.resolver.build.targets"))
      server.workspaceBuildTargets()
    }

    val projectNodeFuture: CompletableFuture[DataNode[ProjectData]] =
      targetsRequest
        .thenCompose { targetsResponse =>
          reporter.finishTask(targetsEventId, BspBundle.message("bsp.resolver.build.targets"), new SuccessResultImpl())

          val targets = targetsResponse.getTargets.asScala.toList

          val td = targetData(targets, structureEventId)

          td.thenApply[DataNode[ProjectData]] { data =>
            val sources = data.sources.map(_.getItems.asScala).getOrElse {List.empty[SourcesItem]}
            val depSources = data.dependencySources.map(_.getItems.asScala).getOrElse {List.empty[DependencySourcesItem]}
            val resources = data.resources.map(_.getItems.asScala).getOrElse {List.empty[ResourcesItem]}
            val outputPaths = data.outputPaths.map(_.getItems.asScala).getOrElse {List.empty[OutputPathsItem]}
            val scalacOptions = data.scalacOptions.map(_.getItems.asScala).getOrElse {List.empty[ScalacOptionsItem]}
            val javacOptions = data.javacOptions.map(_.getItems.asScala).getOrElse {List.empty[JavacOptionsItem]}

            val descriptions = calculateModuleDescriptions(
              targets, scalacOptions.toSeq, javacOptions.toSeq, sources.toSeq, resources.toSeq, outputPaths.toSeq, depSources.toSeq
            )
            projectNode(workspace, descriptions, rootExclusions(workspace), serverInfo.displayName)
          }
          .reportFinished(
            reporter,
            structureEventId,
            BspBundle.message("bsp.resolver.build.structure"),
            BspBundle.message("bsp.resolver.resolving.build.structure.failed")
          )
      }

    projectNodeFuture
  }

  private def runImport(workspace: File, executionSettings: BspExecutionSettings)
                       (implicit reporter: BuildReporter) = {
    def notifications(implicit reporter: BuildReporter): NotificationAggregator[BuildMessages] =
    (messages, notification) => notification match {
      case BspNotifications.LogMessage(params) =>
        //noinspection ReferencePassedToNls
        reporter.log(params.getMessage)
        messages.message(params.getMessage)
      case _ =>
        messages
    }

    val preImportMessages = preImport(executionSettings, workspace)

    val communication = BspCommunication.forWorkspace(workspace, executionSettings.config)
    importState = BspTask(communication)

    preImportMessages match {
      case Success(messages) if messages.status == BuildMessages.OK =>
        val projectJob: BspJob[(DataNode[ProjectData], BuildMessages)] =
          communication.run(requests(workspace)(_, _,reporter), BuildMessages.empty, notifications, reporter.log)

        val value = waitForProjectCancelable(projectJob)
        value match {
          case Success((data, _)) =>
            reporter.finish(messages)
            data
          case Failure(BspTaskCancelled) =>
            reporter.finishCanceled()
            throw BspErrorMessage(BspBundle.message("bsp.resolver.refresh.canceled"))
          case Failure(Bsp4JJobFailure(err, messages: BuildMessages)) =>
            if (err == BspTaskCancelled) {
              Log.error("BspTaskCancelled should not be wrapped in Bsp4JJobFailure")
            }
            val newLine = System.lineSeparator()
            val joinedMessage = err.getMessage + newLine + newLine + messages.messages.mkString(newLine)
            val ansiColorCodePattern = "\\u001B?\\[[0-9;]+m".r
            val cleanMsg = ansiColorCodePattern.replaceAllIn(joinedMessage, "")
            reporter.finishWithFailure(err)
            throw new ExternalSystemException(cleanMsg)
          case Failure(err: Throwable) =>
            reporter.finishWithFailure(err)
            throw err
        }
      case Success(messages) =>
        reporter.finish(messages)
        if (messages.status == BuildMessages.Canceled)
          throw BspErrorMessage(BspBundle.message("bsp.resolver.refresh.canceled"))
        else
          throw BspErrorMessage(BspBundle.message("bsp.resolver.import.failed"))
      case Failure(x) =>
        reporter.finishWithFailure(x)
        throw x
    }
  }

  // special handling for sbt projects: run bloopInstall first
  // TODO support other bloop-enabled build tools as well
  private def preImport(
    executionSettings: BspExecutionSettings,
    workspace: File
  )(implicit reporter: BuildReporter): Try[BuildMessages] = {
    if (executionSettings.runPreImportTask) {
      val preImportTask = executionSettings.preImportTask
      val config = executionSettings.config
      installBSP(workspace, preImportTask, config)
    }
    else EmptyBuildMessagesSuccess
  }

  private val EmptyBuildMessagesSuccess: Success[BuildMessages] = Success(BuildMessages.empty.status(BuildMessages.OK))

  private def installBSP(
    workspace: File,
    preImportTask: BspProjectSettings.PreImportConfig,
    bspServerConfig: BspProjectSettings.BspServerConfig
  )(implicit reporter: BuildReporter): Try[BuildMessages] = {
    def isSbtProject(workspace: File) = new File(workspace, "build.sbt").exists()

    //TODO: runBloopInstall changes `importState` inside
    // however `MillProjectImportProvider.bspInstall(workspace)`
    // The latter was added by contributor, so this might be just a bug?
    preImportTask match {
      case BspProjectSettings.NoPreImport =>
        EmptyBuildMessagesSuccess
      case BspProjectSettings.AutoPreImport =>
        if (bspServerConfig == BspProjectSettings.AutoConfig && bloopConfigDir(workspace).isDefined && isSbtProject(workspace))
          runBloopInstall(workspace)
        else if (MillProjectImportProvider.canImport(workspace))
          MillProjectImportProvider.bspInstall(workspace)
        else EmptyBuildMessagesSuccess
      case BspProjectSettings.BloopSbtPreImport =>
        runBloopInstall(workspace)
      case BspProjectSettings.MillBspPreImport =>
        MillProjectImportProvider.bspInstall(workspace)
    }
  }

  @tailrec private def waitForProjectCancelable(projectJob: BspJob[(DataNode[ProjectData], BuildMessages)]): Try[(DataNode[ProjectData], BuildMessages)] =

    importState match {
      case Active | PreImportTask(_) | BspTask(_) =>
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
                          listener: ExternalSystemTaskNotificationListener): Boolean = {
    def doCancel(f: =>Unit) = {
      listener.beforeCancel(taskId)
      f
      importState = Inactive
      listener.onCancel(taskId)
      true
    }

    importState match {
      case PreImportTask(preImporter) =>
        doCancel { preImporter.cancel() }
      case BspTask(_) =>
        doCancel {}
      case Active =>
        doCancel {}
      case Inactive =>
        false
    }
  }

  private def runBloopInstall(baseDir: File)(implicit reporter: BuildReporter) =
    BspJdkUtil.findOrCreateBestJdkForProject(None) match {
      case Some(sdk) =>
        val preImporter = BloopPreImporter(baseDir, sdk)
        importState = PreImportTask(preImporter)
        preImporter.run()
      case None => Failure(BspNoJdkConfiguredError)
    }

}

object BspProjectResolver {
  private val Log = Logger.getInstance(this.getClass)

  private sealed abstract class ImportState
  private case object Inactive extends ImportState
  private case object Active extends ImportState
  private case class PreImportTask(dumper: PreImporter) extends ImportState
  private case class BspTask(communication: BspCommunication) extends ImportState

  //noinspection ReferencePassedToNls
  private[importing] def targetData(targets: List[BuildTarget], parentId: EventId)
                                   (implicit bsp: BspServer, serverInfo: BuildServerInfo, reporter: BuildReporter):
  CompletableFuture[TargetData] = {
    val targetIds = targets.map(_.getId).asJava

    val sourcesEventId = BuildMessages.randomEventId
    val sourcesParams = new SourcesParams(targetIds)
    val message = BspBundle.message("bsp.resolver.sources")
    reporter.startTask(sourcesEventId, Some(parentId), message)
    val sources = bsp.buildTargetSources(sourcesParams)
      .catchBspErrors
      .reportFinished(reporter, sourcesEventId, message, BspBundle.message("bsp.resolver.request.failed.buildtarget.sources"))

    val depSources = if (serverInfo.capabilities.isDependencySourcesProvider) {
      val eventId = BuildMessages.randomEventId
      val depSourcesParams = new DependencySourcesParams(targetIds)
      val message = "dependency sources"
      reporter.startTask(eventId, Some(parentId), message)
      bsp.buildTargetDependencySources(depSourcesParams)
        .catchBspErrors
        .reportFinished(reporter, eventId, message, BspBundle.message("bsp.resolver.request.failed.buildtarget.dependencysources"))
    } else {
      val emptyResult = new DependencySourcesResult(Collections.emptyList())
      CompletableFuture.completedFuture[Try[DependencySourcesResult]](Success(emptyResult))
    }

    val resources = if (serverInfo.capabilities.isResourcesProvider) {
      val eventId = BuildMessages.randomEventId
      val resourcesParams = new ResourcesParams(targetIds)
      val message = "resources"
      reporter.startTask(eventId, Some(parentId), message)
      bsp.buildTargetResources(resourcesParams)
        .catchBspErrors
        .reportFinished(reporter, eventId, message, BspBundle.message("bsp.resolver.request.failed.buildtarget.resources"))
    } else {
      val emptyResult = new ResourcesResult(Collections.emptyList())
      CompletableFuture.completedFuture[Try[ResourcesResult]](Success(emptyResult))
    }

    val outputPaths: CompletableFuture[Try[OutputPathsResult]] = if (serverInfo.capabilities.isOutputPathsProvider) {
      val params = new OutputPathsParams(targetIds)
      val eventId = BuildMessages.randomEventId
      val message = BspBundle.message("bsp.resolver.outputpaths")
      reporter.startTask(eventId, Some(parentId), message)
      bsp.buildTargetOutputPaths(params)
        .catchBspErrors
        .reportFinished(reporter, eventId, message, BspBundle.message("bsp.resolver.request.failed.buildtarget.outputpaths"))
    } else {
      val emptyResult = new OutputPathsResult(Collections.emptyList())
      CompletableFuture.completedFuture[Try[OutputPathsResult]](Success(emptyResult))
    }

    val scalacOptions = fetchScalacOptions(targets, parentId)
    val javacOptions = fetchJavacOptions(targets, parentId)

    CompletableFuture
      .allOf(sources, depSources, resources, outputPaths, scalacOptions, javacOptions)
      .thenApply(_ => TargetData(sources.get, depSources.get, resources.get, outputPaths.get, scalacOptions.get, javacOptions.get))
  }

  //noinspection ReferencePassedToNls
  private def fetchScalacOptions(targets: List[BuildTarget], parentId: EventId)(implicit bsp: BspServer, reporter: BuildReporter) = {
    val scalaTargetIds = targets
      .filter(t => t.getLanguageIds.contains("scala") && t.getDataKind == BuildTargetDataKind.SCALA || t.getDataKind == BuildTargetDataKind.SBT)
      .map(_.getId).asJava

    if (! scalaTargetIds.isEmpty) {
      val eventId = BuildMessages.randomEventId
      val message = "scalac options"
      reporter.startTask(eventId, Some(parentId), message)

      val scalacOptionsParams = new ScalacOptionsParams(scalaTargetIds)
      bsp.buildTargetScalacOptions(scalacOptionsParams)
        .catchBspErrors
        .reportFinished(reporter, eventId, message, BspBundle.message("bsp.resolver.request.failed.buildtarget.scalacoptions"))

    } else {
      val emptyResult = new ScalacOptionsResult(Collections.emptyList())
      CompletableFuture.completedFuture[Try[ScalacOptionsResult]](Success(emptyResult))
    }
  }

  //noinspection ReferencePassedToNls
  private def fetchJavacOptions(targets: List[BuildTarget], parentId: EventId)(implicit bsp: BspServer, reporter: BuildReporter) = {
    val javaTargetIds = targets
      .filter(t => t.getLanguageIds.contains("java") && t.getDataKind == BuildTargetDataKind.JVM)
      .map(_.getId).asJava

    if (! javaTargetIds.isEmpty) {
      val eventId = BuildMessages.randomEventId
      val message = "javac options"
      reporter.startTask(eventId, Some(parentId), message)

      val javacOptionsParams = new JavacOptionsParams(javaTargetIds)
      bsp.buildTargetJavacOptions(javacOptionsParams)
        .catchBspErrors
        .reportFinished(reporter, eventId, message, BspBundle.message("bsp.resolver.request.failed.buildtarget.javacoptions"))

    } else {
      val emptyResult = new JavacOptionsResult(Collections.emptyList())
      CompletableFuture.completedFuture[Try[JavacOptionsResult]](Success(emptyResult))
    }
  }

  implicit class BuildServerCapabilitiesOps(private val capabilities: BuildServerCapabilities) extends AnyVal {
    def isDependencySourcesProvider: Boolean =
      Option(capabilities.getDependencySourcesProvider).exists(_.booleanValue())

    def isResourcesProvider: Boolean =
      Option(capabilities.getResourcesProvider).exists(_.booleanValue())

    def isOutputPathsProvider: Boolean =
      Option(capabilities.getOutputPathsProvider).exists(_.booleanValue())
  }
  
  private[importing] def rootExclusions(workspace: File): List[File] = List(
    new File(workspace, BspUtil.BloopConfigDirName),
    new File(workspace, BspConnectionConfig.BspWorkspaceConfigDirName),
  ) ++ BspUtil.compilerOutputDirFromConfig(workspace).toList
}
