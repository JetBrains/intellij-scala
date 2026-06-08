package org.jetbrains.sbt.actions

import com.intellij.build.events.BuildEvents
import com.intellij.build.events.impl.{FailureResultImpl, SkippedResultImpl, SuccessResultImpl}
import com.intellij.build.{DefaultBuildDescriptor, SyncViewManager}
import com.intellij.execution.process.ProcessOutputType
import com.intellij.openapi.actionSystem.{ActionUpdateThread, AnAction, AnActionEvent}
import com.intellij.openapi.externalSystem.model.task.{ExternalSystemTaskId, ExternalSystemTaskType}
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.progress.{ProgressIndicator, Task}
import com.intellij.openapi.vfs.{VfsUtil, VirtualFileManager}
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.scala.build.BuildMessages
import org.jetbrains.sbt.icons.Icons
import org.jetbrains.sbt.process.SbtRunner
import org.jetbrains.sbt.project.{SbtExternalSystemManager, SbtProjectSystem}
import org.jetbrains.sbt.{SbtBundle, SbtUtil, SbtVersionCapabilities, eelDescriptor, normalizedLocalPath}

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
        val reporter = new GenerateManagedSourcesReporter(viewManager, taskId)

        val settings = SbtExternalSystemManager.executionSettingsFor(project)
        val projectBasePath = Path.of(settings.realProjectPath)

        val descriptor = new DefaultBuildDescriptor(taskId, SbtBundle.message("sbt.generate.managed.sources.action.title"), projectBasePath.toString, System.currentTimeMillis())
        descriptor.setActivateToolWindowWhenAdded(false)
        descriptor.setActivateToolWindowWhenFailed(true)

        {
          val event = BuildEvents.getInstance()
            .startBuild(SbtBundle.message("sbt.generate.managed.sources.action.title"), descriptor)
            .build()
          viewManager.onEvent(taskId, event)
        }

        def reportFailure(@Nullable throwable: Throwable): Unit = {
          {
            val sbtOutput = reporter.outputLines.mkString(start = "", sep = System.lineSeparator(), end = System.lineSeparator())
            val event = BuildEvents.getInstance()
              .output(sbtOutput)
              .withId(taskId)
              .withOutputType(ProcessOutputType.STDOUT)
              .build()
            viewManager.onEvent(taskId, event)
          }
          {
            val failureWord = SbtBundle.message("sbt.generate.managed.sources.task.result.failure")
            val failureMessage = SbtBundle.message("sbt.generate.managed.sources.task.result.failure.message")
            val failureResult = new FailureResultImpl(failureMessage, throwable)
            val events = BuildEvents.getInstance()
              .finishBuild(taskId, failureWord, failureResult)
              .withTime(System.currentTimeMillis())
              .build()
            viewManager.onEvent(taskId, events)
          }
        }

        try {
          val launcher = SbtUtil.getLauncherJar(settings)

          val sbtVersion = SbtUtil.detectSbtVersion(projectBasePath, launcher)
          val addPluginCommandSupported = SbtVersionCapabilities.isAddPluginCommandSupported(sbtVersion)

          if (!addPluginCommandSupported) {
            val notSupportedWord = SbtBundle.message("sbt.generate.managed.sources.action.not.supported")
            val notSupportedMessage = SbtBundle.message("sbt.generate.managed.sources.action.not.supported.message", sbtVersion.minor)
            val failureResult = new FailureResultImpl(notSupportedMessage)
            val finishEvent = BuildEvents.getInstance()
              .finishBuild(taskId, notSupportedWord, failureResult)
              .withTime(System.currentTimeMillis())
              .build()
            viewManager.onEvent(taskId, finishEvent)
            return
          }

          val descriptor = projectBasePath.eelDescriptor
          val sbtFileContent = SbtUtil.sbtStructurePluginDeclaration(
            sbtVersion,
            SbtUtil.getRepoDir(descriptor)
          ).mkString(System.lineSeparator())
          val tmpPluginsSbtFile = SbtUtil.createTemporarySbtFile(sbtFileContent, descriptor, Option(project))
          val setupOptions = Seq(s"-addPluginSbtFile=${tmpPluginsSbtFile.normalizedLocalPath}")

          val generateCommand = "show " + SbtUtil.sbtStructureGlobalCommand("ideaGenerateAllManagedSources", sbtVersion)
          val sbtResult = SbtRunner().runSbt(
            indicator,
            projectBasePath,
            settings.vmExecutable.toPath,
            settings.vmOptions,
            settings.userSetEnvironment,
            launcher,
            settings.sbtOptions,
            setupOptions,
            generateCommand,
            SbtBundle.message("sbt.generate.managed.sources.task.progress.title"),
            settings.passParentEnvironment,
            timingCollector = None,
            project = Some(project),
          )(using reporter)

          sbtResult match {
            case Success(buildMessages) if buildMessages.status == BuildMessages.Error => reportFailure(null)

            case Success(buildMessages) if buildMessages.status == BuildMessages.Canceled =>
              {
                val canceledMessage = SbtBundle.message("sbt.generate.managed.sources.task.result.canceled.message")
                val event = BuildEvents.getInstance()
                  .output(canceledMessage)
                  .withId(taskId)
                  .withOutputType(ProcessOutputType.STDOUT)
                  .build()
                viewManager.onEvent(taskId, event)
              }
              {
                val canceledWord = SbtBundle.message("sbt.generate.managed.sources.task.result.canceled")
                val finishEvent = BuildEvents.getInstance()
                  .finishBuild(taskId, canceledWord, new SkippedResultImpl())
                  .withTime(System.currentTimeMillis())
                  .build()
                viewManager.onEvent(taskId, finishEvent)
              }

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
                  VfsUtil.markDirtyAndRefresh(false, false, true, virtualFiles*)

                  {
                    val output = lines.mkString(start = "", sep = System.lineSeparator(), end = System.lineSeparator())
                    val event = BuildEvents.getInstance()
                      .output(output)
                      .withId(taskId)
                      .withOutputType(ProcessOutputType.STDOUT)
                      .build()
                    viewManager.onEvent(taskId, event)
                  }
                  {
                    val successEvent = BuildEvents.getInstance()
                      .finishBuild(taskId, SbtBundle.message("sbt.generate.managed.sources.task.result.success"), new SuccessResultImpl())
                      .withTime(System.currentTimeMillis())
                      .build()
                    viewManager.onEvent(taskId, successEvent)
                  }
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
