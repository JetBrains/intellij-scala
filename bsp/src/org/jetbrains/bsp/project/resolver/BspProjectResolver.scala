package org.jetbrains.bsp.project.resolver

import java.io.File
import java.util.Collections
import java.util.concurrent.CompletableFuture

import ch.epfl.scala.bsp4j._
import com.intellij.build.events.impl.SuccessResultImpl
import com.intellij.openapi.externalSystem.model.project._
import com.intellij.openapi.externalSystem.model.task.{ExternalSystemTaskId, ExternalSystemTaskNotificationListener}
import com.intellij.openapi.externalSystem.model.{DataNode, ExternalSystemException}
import com.intellij.openapi.externalSystem.service.project.ExternalSystemProjectResolver
import com.intellij.openapi.projectRoots.{JavaSdk, ProjectJdkTable}
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import org.jetbrains.bsp.BspUtil._
import org.jetbrains.bsp.project.resolver.BspProjectResolver._
import org.jetbrains.bsp.project.resolver.BspResolverDescriptors._
import org.jetbrains.bsp.project.resolver.BspResolverLogic._
import org.jetbrains.bsp.protocol.session.Bsp4JJobFailure
import org.jetbrains.bsp.protocol.session.BspSession.{BspServer, NotificationAggregator}
import org.jetbrains.bsp.protocol.{BspCommunication, BspConnectionConfig, BspJob, BspNotifications}
import org.jetbrains.bsp.settings.BspExecutionSettings
import org.jetbrains.bsp.{BspBundle, BspErrorMessage, BspTaskCancelled, BspUtil}
import org.jetbrains.plugins.scala.build.BuildMessages.EventId
import org.jetbrains.plugins.scala.build.{BuildMessages, BuildReporter, ExternalSystemNotificationReporter}
import org.jetbrains.plugins.scala.buildinfo.BuildInfo
import org.jetbrains.plugins.scala.project.Version
import org.jetbrains.sbt.SbtUtil.{detectSbtVersion, getDefaultLauncher, sbtVersionParam, upgradedSbtVersion}
import org.jetbrains.sbt.project.SbtProjectResolver.ImportCancelledException
import org.jetbrains.sbt.project.structure.{Cancellable, MillPreImporter, SbtStructureDump}
import org.jetbrains.sbt.project.{MillProjectImportProvider, SbtExternalSystemManager, SbtProjectImportProvider}
import org.jetbrains.sbt.{Sbt, SbtUtil}

import scala.annotation.tailrec
import scala.collection.JavaConverters._
import scala.concurrent.duration._
import scala.concurrent.{Await, TimeoutException}
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
      if (workspaceCreationFile.isDirectory) workspaceCreationFile
      else workspaceCreationFile.getParentFile

    importState = Active

    reporter.start()

    val result = if (isPreviewMode) {
      val modules = ProjectModules(Nil, Nil)
      reporter.finish(BuildMessages.empty.status(BuildMessages.OK))
      projectNode(workspace, modules, rootExclusions(workspace))
    } else {
      runImport(workspace, executionSettings)
    }

    importState = Inactive

    result

  }

  private def requests(workspace: File)
                      (implicit server: BspServer, capabilities: BuildServerCapabilities, reporter: BuildReporter)
  : CompletableFuture[DataNode[ProjectData]] = {

    val structureEventId = BuildMessages.randomEventId
    reporter.startTask(structureEventId, None, BspBundle.message("bsp.resolver.resolving.build.structure"))
    val targetsEventId = BuildMessages.randomEventId
    reporter.startTask(targetsEventId, Some(structureEventId), BspBundle.message("bsp.resolver.build.targets"))
    val targetsRequest = server.workspaceBuildTargets()

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
            val scalacOptions = data.scalacOptions.map(_.getItems.asScala).getOrElse {List.empty[ScalacOptionsItem]}

            val descriptions = calculateModuleDescriptions(
              targets, scalacOptions, sources, resources, depSources
            )
            projectNode(workspace, descriptions, rootExclusions(workspace))
          }
          .reportFinished(
            reporter, structureEventId,
            BspBundle.message("bsp.resolver.build.structure"),
            BspBundle.message("bsp.resolver.resolving.build.structure.failed"))
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

    // special handling for sbt projects: run bloopInstall first
    // TODO support other bloop-enabled build tools as well
    val vfile = LocalFileSystem.getInstance().findFileByIoFile(workspace)
    val sbtMessages =
      if (
        executionSettings.runPreImportTask &&
        BspConnectionConfig.workspaceConfigurations(workspace).isEmpty) {
        
          if (SbtProjectImportProvider.canImport(vfile))
            runBloopInstall(workspace)
          else if (MillProjectImportProvider.canImport(vfile))
            runMillBspInstall(workspace)
          else
            Success(BuildMessages.empty.status(BuildMessages.OK))
      } else Success(BuildMessages.empty.status(BuildMessages.OK))

    val communication = BspCommunication.forWorkspace(workspace)
    importState = BspTask(communication)

    sbtMessages match {
      case Success(messages) if messages.status == BuildMessages.OK =>
        val projectJob: BspJob[(DataNode[ProjectData], BuildMessages)] = communication.run(requests(workspace)(_,_,reporter),BuildMessages.empty, notifications, reporter.log)
        waitForProjectCancelable(projectJob) match {
          case Success((data, _)) =>
            reporter.finish(messages)
            data
          case Failure(BspTaskCancelled) =>
            reporter.finishCanceled()
            null
          case Failure(Bsp4JJobFailure(err, messages: BuildMessages)) =>
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
        throw BspErrorMessage(BspBundle.message("bsp.resolver.import.failed"))
      case Failure(x) =>
        reporter.finishWithFailure(x)
        throw x
    }
  }

  @tailrec private def waitForProjectCancelable[T](projectJob: BspJob[(DataNode[ProjectData], BuildMessages)]): Try[(DataNode[ProjectData], BuildMessages)] =

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
                          listener: ExternalSystemTaskNotificationListener): Boolean =
    importState match {
      case PreImportTask(dumper) =>
        listener.beforeCancel(taskId)
        dumper.cancel()
        importState = Inactive
        listener.onCancel(taskId)
        true
      case BspTask(_) =>
        listener.beforeCancel(taskId)
        importState = Inactive
        listener.onCancel(taskId)
        true
      case Active =>
        importState = Inactive
        listener.onCancel(taskId)
        true
      case Inactive =>
        false
    }

  private def runMillBspInstall(baseDir: File)(implicit reporter: BuildReporter) = Try {
    val preImporter = MillPreImporter.setupBsp(baseDir)
    importState = PreImportTask(preImporter)
    preImporter.waitFinish()

    BuildMessages.empty.status(BuildMessages.OK)
  }.recoverWith {
    case fail => Failure(ImportCancelledException(fail))
  }

  private def runBloopInstall(baseDir: File)(implicit reporter: BuildReporter) = {
    val jdkType = JavaSdk.getInstance()
    val jdk = ProjectJdkTable.getInstance().findMostRecentSdkOfType(jdkType)
    val jdkExe = new File(jdkType.getVMExecutablePath(jdk)) // TODO error when none, offer jdk config
    val jdkHome = Option(jdk.getHomePath).map(new File(_))
    val sbtLauncher = SbtUtil.getDefaultLauncher

    val injectedPlugins = s"""addSbtPlugin("ch.epfl.scala" % "sbt-bloop" % "${BuildInfo.bloopVersion}")"""
    val pluginFile = FileUtil.createTempFile("idea",Sbt.Extension, true)
    val pluginFilePath = SbtUtil.normalizePath(pluginFile)
    FileUtil.writeToFile(pluginFile, injectedPlugins)

    val injectedSettings = """bloopExportJarClassifiers in Global := Some(Set("sources"))"""
    val settingsFile = FileUtil.createTempFile(baseDir, "idea-bloop", Sbt.Extension, true)
    FileUtil.writeToFile(settingsFile, injectedSettings)

    val sbtCommandArgs = List(
      "early(addPluginSbtFile=\"\"\"" + pluginFilePath + "\"\"\")"
    )
    val sbtCommands = "bloopInstall"

    val projectSbtVersion = Version(detectSbtVersion(baseDir, getDefaultLauncher))
    val sbtVersion = upgradedSbtVersion(projectSbtVersion)
    val upgradeParam =
      if (sbtVersion > projectSbtVersion)
        List(sbtVersionParam(sbtVersion))
      else List.empty

    val vmArgs = SbtExternalSystemManager.getVmOptions(Seq.empty, jdkHome) ++ upgradeParam

    try {
      val dumper = new SbtStructureDump()
      importState = PreImportTask(dumper)
      dumper.runSbt(
        baseDir, jdkExe, vmArgs,
        Map.empty, sbtLauncher, sbtCommandArgs, sbtCommands,
        reporter,
        BspBundle.message("bsp.resolver.creating.bloop.configuration.from.sbt"),
      )
    } finally {
      settingsFile.delete()
    }
  }

}

object BspProjectResolver {

  private sealed abstract class ImportState
  private case object Inactive extends ImportState
  private case object Active extends ImportState
  private case class PreImportTask(dumper: Cancellable) extends ImportState
  private case class BspTask(communication: BspCommunication) extends ImportState

  //noinspection ReferencePassedToNls
  private[resolver] def targetData(targets: List[BuildTarget], parentId: EventId)
                                  (implicit bsp: BspServer, capabilities: BuildServerCapabilities, reporter: BuildReporter):
  CompletableFuture[TargetData] = {
    val targetIds = targets.map(_.getId).asJava

    val sourcesEventId = BuildMessages.randomEventId
    val sourcesParams = new SourcesParams(targetIds)
    val message = BspBundle.message("bsp.resolver.sources")
    reporter.startTask(sourcesEventId, Some(parentId), message)
    val sources = bsp.buildTargetSources(sourcesParams)
      .catchBspErrors
      .reportFinished(reporter, sourcesEventId, message, BspBundle.message("bsp.resolver.request.failed.buildtarget.sources"))

    val depSources = if (isDependencySourcesProvider) {
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

    val resources = if (isResourcesProvider) {
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
        .reportFinished(reporter, eventId, message, BspBundle.message("bsp.resolver.request.failed.buildtarget.scalacoptions"))

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

  private[resolver] def rootExclusions(workspace: File): List[File] = List(
    new File(workspace, BspUtil.BloopConfigDirName),
    new File(workspace, BspConnectionConfig.BspWorkspaceConfigDirName),
  ) ++ BspUtil.compilerOutputDirFromConfig(workspace).toList
}
