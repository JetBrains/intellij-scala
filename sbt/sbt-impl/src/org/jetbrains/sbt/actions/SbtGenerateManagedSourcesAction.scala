package org.jetbrains.sbt.actions

import com.intellij.build.events.impl.{FailureResultImpl, FinishBuildEventImpl, OutputBuildEventImpl, SkippedResultImpl, StartBuildEventImpl, SuccessResultImpl}
import com.intellij.build.{DefaultBuildDescriptor, SyncViewManager}
import com.intellij.openapi.actionSystem.{ActionUpdateThread, AnAction, AnActionEvent}
import com.intellij.openapi.externalSystem.model.task.{ExternalSystemTaskId, ExternalSystemTaskType}
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.progress.{ProgressIndicator, Task}
import com.intellij.openapi.vfs.{VfsUtil, VirtualFileManager}
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.scala.build.BuildMessages
import org.jetbrains.sbt.buildinfo.BuildInfo
import org.jetbrains.sbt.icons.Icons
import org.jetbrains.sbt.project.structure.SbtStructureDump
import org.jetbrains.sbt.project.{SbtExternalSystemManager, SbtProjectSystem}
import org.jetbrains.sbt.{SbtBundle, SbtUtil, SbtVersionCapabilities}

import java.nio.file.{Files, Path}
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}

private final class SbtGenerateManagedSourcesAction extends AnAction(
  SbtBundle.message("sbt.generate.managed.sources.action.title"),
  SbtBundle.message("sbt.generate.managed.sources.action.description"),
  Icons.SBT_GENERATE_MANAGED_SOURCES
) {

  override def actionPerformed(e: AnActionEvent): Unit = {
    val project = e.getProject

    val task = new Task.Backgroundable(project, SbtBundle.message("sbt.generate.managed.sources.task.progress.title"), true) {
      override def run(indicator: ProgressIndicator): Unit = {
        val viewManager = project.getService(classOf[SyncViewManager])
        val taskId = ExternalSystemTaskId.create(SbtProjectSystem.Id, ExternalSystemTaskType.EXECUTE_TASK, project)
        val reporter = new GenerateManagedSourcesReporter()

        val settings = SbtExternalSystemManager.executionSettingsFor(project)
        val projectBasePath = Path.of(settings.realProjectPath)

        val descriptor = new DefaultBuildDescriptor(taskId, SbtBundle.message("sbt.generate.managed.sources.action.title"), projectBasePath.toString, System.currentTimeMillis())
        descriptor.setActivateToolWindowWhenAdded(false)
        descriptor.setActivateToolWindowWhenFailed(true)
        viewManager.onEvent(taskId, new StartBuildEventImpl(descriptor, SbtBundle.message("sbt.generate.managed.sources.action.title")))

        def reportFailure(@Nullable throwable: Throwable): Unit = {
          val sbtOutput = reporter.outputLines.mkString(start = "", sep = System.lineSeparator(), end = System.lineSeparator())
          viewManager.onEvent(taskId, new OutputBuildEventImpl(taskId, null, sbtOutput, true))
          val failureWord = SbtBundle.message("sbt.generate.managed.sources.task.result.failure")
          val failureMessage = SbtBundle.message("sbt.generate.managed.sources.task.result.failure.message")
          val failureResult = new FailureResultImpl(failureMessage, throwable)
          val finishEvent = new FinishBuildEventImpl(taskId, null, System.currentTimeMillis(), failureWord, failureResult)
          viewManager.onEvent(taskId, finishEvent)
        }

        try {
          val launcher = SbtUtil.getLauncherJar(settings)

          val sbtVersion = SbtUtil.detectSbtVersion(projectBasePath, launcher)
          val sbtStructurePluginBinVersion = SbtUtil.structurePluginBinaryVersion(sbtVersion)
          val addPluginCommandSupported = SbtVersionCapabilities.isAddPluginCommandSupported(sbtVersion)

          if (!addPluginCommandSupported) {
            val notSupportedWord = SbtBundle.message("sbt.generate.managed.sources.action.not.supported")
            val notSupportedMessage = SbtBundle.message("sbt.generate.managed.sources.action.not.supported.message", sbtVersion.minor)
            val failureResult = new FailureResultImpl(notSupportedMessage)
            val finishEvent = new FinishBuildEventImpl(taskId, null, System.currentTimeMillis(), notSupportedWord, failureResult)
            viewManager.onEvent(taskId, finishEvent)
            return
          }

          val repoPath = SbtUtil.normalizePath(SbtUtil.getRepoDir)
          val pluginsSbt =
            raw"""resolvers += MavenCache("Scala Plugin Bundled Repository", file(raw"$repoPath"))
                 |
                 |addSbtPlugin("org.jetbrains.scala" % "sbt-structure-extractor" % "${BuildInfo.sbtStructureVersion}", "$sbtStructurePluginBinVersion")
                 |""".stripMargin

          val tmpPluginsSbtFile = Files.createTempFile("idea-gen-managed-sources", ".sbt")
          Files.writeString(tmpPluginsSbtFile, pluginsSbt)
          val setupOptions = Seq(s"-addPluginSbtFile=${tmpPluginsSbtFile.toRealPath()}")
          tmpPluginsSbtFile.toFile.deleteOnExit()

          val generateCommand = "show " + SbtUtil.sbtStructureGlobalCommand("ideaGenerateAllManagedSources", sbtVersion)
          val sbtResult = new SbtStructureDump().runSbt(
            projectBasePath.toFile,
            settings.vmExecutable,
            settings.vmOptions,
            settings.userSetEnvironment,
            launcher.toFile,
            settings.sbtOptions,
            setupOptions,
            generateCommand,
            SbtBundle.message("sbt.generate.managed.sources.task.progress.title"),
            settings.passParentEnvironment,
          )(indicator)(using reporter)

          sbtResult match {
            case Success(buildMessages) if buildMessages.status == BuildMessages.Error => reportFailure(null)

            case Success(buildMessages) if buildMessages.status == BuildMessages.Canceled =>
              val canceledWord = SbtBundle.message("sbt.generate.managed.sources.task.result.canceled")
              val canceledMessage = SbtBundle.message("sbt.generate.managed.sources.task.result.canceled.message")
              viewManager.onEvent(taskId, new OutputBuildEventImpl(taskId, null, canceledMessage, true))
              val finishEvent = new FinishBuildEventImpl(taskId, null, System.currentTimeMillis(), canceledWord, new SkippedResultImpl())
              viewManager.onEvent(taskId, finishEvent)

            case Success(_) =>
              val lines = reporter.outputLines
              val containsErrors = lines.exists(_.startsWith("[error]"))

              if (containsErrors) {
                reportFailure(null)
              } else {
                try {
                  def realFile(path: Path): Boolean = Files.exists(path) && Files.isRegularFile(path)
                  val generatedSources = lines.collect { case s"[info] * $path" => path }
                    .flatMap(path => Try(Path.of(path).toRealPath()).filter(realFile).toOption)
                  val fileManager = VirtualFileManager.getInstance()
                  val virtualFiles = generatedSources.flatMap(p => Option(fileManager.refreshAndFindFileByNioPath(p)))
                  VfsUtil.markDirtyAndRefresh(false, false, true, virtualFiles: _*)
                  val output = lines.mkString(start = "", sep = System.lineSeparator(), end = System.lineSeparator())
                  viewManager.onEvent(taskId, new OutputBuildEventImpl(taskId, null, output, true))
                  val successWord = SbtBundle.message("sbt.generate.managed.sources.task.result.success")
                  val successResult = new SuccessResultImpl()
                  val successEvent = new FinishBuildEventImpl(taskId, null, System.currentTimeMillis(), successWord, successResult)
                  viewManager.onEvent(taskId, successEvent)
                } catch {
                  case NonFatal(t) => reportFailure(t)
                }
              }

            case Failure(exception) => reportFailure(exception)
          }
        } catch {
          case NonFatal(t) => reportFailure(t)
        }
      }
    }

    // Make sure all modified files are saved to disk before invoking sbt.
    FileDocumentManager.getInstance().saveAllDocuments()
    task.queue()
  }

  override def update(e: AnActionEvent): Unit = {
    val project = e.getProject
    if (project eq null) return
    val enabled = SbtUtil.isSbtProject(project)
    e.getPresentation.setEnabledAndVisible(enabled)
  }

  override def getActionUpdateThread: ActionUpdateThread = ActionUpdateThread.BGT
}
