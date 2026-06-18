package org.jetbrains.sbt.shell

import com.intellij.debugger.DebuggerManagerEx
import com.intellij.debugger.settings.DebuggerSettings
import com.intellij.debugger.ui.HotSwapUI
import com.intellij.execution.Executor
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.notification.{NotificationAction, NotificationType}
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.externalSystem.importing.ImportSpecBuilder
import com.intellij.openapi.externalSystem.model.execution.ExternalSystemTaskExecutionSettings
import com.intellij.openapi.externalSystem.util.{ExternalSystemUtil, ExternalSystemApiUtil as ES}
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.module.{Module, ModuleType}
import com.intellij.openapi.progress.{ProcessCanceledException, ProgressIndicator, ProgressManager, Task}
import com.intellij.openapi.project.Project
import com.intellij.packaging.artifacts.Artifact
import com.intellij.packaging.impl.artifacts.ArtifactUtil
import com.intellij.task.*
import org.jetbrains.annotations.Nullable
import org.jetbrains.concurrency.{AsyncPromise, Promise as IJPromise}
import org.jetbrains.plugins.scala.build.BuildToolWindowReporter.CancelBuildAction
import org.jetbrains.plugins.scala.build.{BuildMessages, BuildToolWindowReporter, CompositeReporter, IndicatorReporter, TaskRunnerResult}
import org.jetbrains.plugins.scala.extensions
import org.jetbrains.plugins.scala.extensions.invokeAndWait
import org.jetbrains.plugins.scala.util.{ExternalSystemVfsUtil, ScalaNotificationGroups}
import org.jetbrains.sbt.project.SbtProjectSystem
import org.jetbrains.sbt.project.module.SbtModuleType
import org.jetbrains.sbt.settings.SbtSettings
import org.jetbrains.sbt.shell.communication.{SbtShellBuildMessagesEventProcessor, SbtShellCommandRequest}
import org.jetbrains.sbt.{SbtBundle, SbtVersion, SbtVersionDetector}

import java.util
import scala.collection.mutable
import scala.concurrent.{Future, Promise}
import scala.jdk.CollectionConverters.{CollectionHasAsScala, SetHasAsScala}
import scala.util.{Failure, Success}

/**
 * This task runner is responsible for delegating the "Build" tasks to sbt shell when it's enabled.
 *
 * When this is activated via `canRun`, this is used to build the project instead of other task runner implementations.
 *
 * Other task runners:
 *  - [[com.intellij.task.impl.JpsProjectTaskRunner]]<br>
 *    the default JPS build task runner that is used in SBT proejcts unless "Use sbt shell for builds" is enabled
 *  - [[org.jetbrains.bsp.project.BspProjectTaskRunner]]<br>
 *    used in BSP projects
 *  - [[org.jetbrains.idea.maven.execution.build.MavenProjectTaskRunner]]<br>
 *    used in Maven projects when delegation to the build tool is enabled
 *  - [[org.jetbrains.plugins.gradle.execution.build.GradleProjectTaskRunner]]<br>
 *    used in Gradle projects when delegation to the build tool is enabled (by default)
 *  - [[org.jetbrains.bazel.buildTask.BazelProjectTaskRunner]]<br>
 *    used in Bazel projects when delegation to the build tool is enabled (by default)
 *
 * @see [[com.intellij.task.impl.ProjectTaskManagerImpl#run]]
 */
final class SbtProjectTaskRunnerImpl
  extends ProjectTaskRunner
    with SbtProjectTaskRunner {

  private val Log = Logger.getInstance(this.getClass)

  //noinspection UsagesOfObsoleteApi
  //The method is not supposed to be unused by the platform and exists only to satisfy the interface and not break any old clients relying on this
  override def canRun(projectTask: ProjectTask): Boolean = {
    Log.error("Method `canRun(ProjectTask)` is obsolete. Use `canRun(Project, ProjectTask, ProjectTaskContext)` instead.")
    false
  }

  override def canRun(
    project: Project,
    projectTask: ProjectTask,
    @Nullable context: ProjectTaskContext
  ): Boolean = canRunImpl(project, projectTask, context)

  private def canRunImpl(
    project: Project,
    projectTask: ProjectTask,
    @Nullable context: ProjectTaskContext
  ): Boolean = {
    val collected = collectSupportedBuildTasks(Seq(projectTask))

    val hasSupportedTasks = collected.moduleBuildTasks.nonEmpty || collected.artifactBuildTasks.nonEmpty
    val result = if (hasSupportedTasks) {
      // Checks if the "Use sbt shell for build" is enabled for one of the modules included in the given artifact.
      // In practice the "Use sbt shell for build" is actually a per-linked-external-project setting, not global per-project.
      // However, here we assume that if "Use sbt shell for build enabled" is enabled for some modules, then we should use it for all modules.
      // Technically, it's not 100% correct - we could construct a project with multiple linked sbt projects with different settings.
      // But in practice I would expect this to be a rare edge case, in combination of using "Build Artifact" IntelliJ IDEA feature
      // that I don't expect to be widely used in SBT projects. It's mostly a legacy thing that it's used in Scala Plugin.
      // Ideally, users should use sbt tasks.
      //
      // NOTE:
      // In practice, when "Build Project" is invoked, there is only one module because `projectTask` is just `ModuleBuildTask` with a `getModule` method.
      //
      // Related: SCL-24287
      val taskModules = extractModulesFromBuildTasks(project, collected)
      taskModules.exists(isUseSbtShellForBuildEnabled)
    } else {
      // For sbt build-definition modules (`-build`), return true from `canRun` only to let sbt runner claim the task.
      // This prevents fallback to JPS (which can start compile server), while actual compilation stays a no-op
      // because build modules are filtered out in `collectSupportedBuildTasks`.
      isSbtBuildModuleTaskDelegatedToSbt(project, projectTask)
    }

    Log.debug(s"canRunImpl: $result (hasSupportedTasks=$hasSupportedTasks, moduleTasks=${collected.moduleBuildTasks.size}, artifactTasks=${collected.artifactBuildTasks.size})")

    result
  }

  private def isSbtBuildModuleTaskDelegatedToSbt(project: Project, projectTask: ProjectTask): Boolean = {
    projectTask match {
      case moduleTask: ModuleBuildTask if isBuildModule(moduleTask) =>
        SbtSettings.getInstance(project).getLinkedProjectsSettings.asScala.exists(_.useSbtShellForBuild)
      case _ =>
        false
    }
  }

  private def isUseSbtShellForBuildEnabled(module: Module): Boolean = {
    val project = module.getProject

    val sbtProjectSettings = SbtSettings.getInstance(project).getLinkedProjectSettings(module)

    sbtProjectSettings.exists(_.useSbtShellForBuild) &&
      ES.isExternalSystemAwareModule(SbtProjectSystem.Id, module)
  }

  override def run(
    project: Project,
    context: ProjectTaskContext,
    tasks: ProjectTask*
  ): IJPromise[ProjectTaskRunner.Result] = {
    Log.debug(s"run start: project=${project.getName}, incomingTasks=${tasks.size}...")

    val supportedBuildTasks = collectSupportedBuildTasks(tasks)
    val buildTasks: Seq[BuildTask] =
      supportedBuildTasks.moduleBuildTasks ++
        supportedBuildTasks.artifactBuildTasks.map(_._1)

    Log.trace(s"run: supportedTasks=${buildTasks.size}, moduleBuildSaks: ${supportedBuildTasks.moduleBuildTasks.size} artifactTasks=${supportedBuildTasks.artifactBuildTasks.size}")

    // IMPORTANT NOTE: The "Build" button action in IDEA and "compile/products" in the SBT root project behave differently.
    // - The IDEA "Build" action always runs the build for all individual modules.
    // - The "products"/"compile" sbt task from the main/root module will do it only for the root project
    //   If the project does not depend on other projects or doesn't aggregate them, then it will compile just the root, just the compile scope
    val taskModules = extractModulesFromBuildTasks(project, supportedBuildTasks)
    val sbtVersion: SbtVersion = getOrDetectSbtVersion(project)
    val sbtBuildCommands: Seq[String] = SbtBuildCommandsFactory.createBuildCommands(sbtVersion, taskModules)

    val sbtBuildPromise = runSbtBuildTasks(project, buildTasks, sbtBuildCommands)
    val needToBuildArtifactAfter = supportedBuildTasks.artifactBuildTasks.nonEmpty
    val resultPromise =
      if (needToBuildArtifactAfter)
        // Unlike plain JPS runner, sbt compile is a separate async phase here.
        // Artifact packaging is chained explicitly and starts only after successful sbt completion.
        SbtJpsArtifactPackagingUtil.chainSbtBuildAndJpsArtifactPackaging(project, context, sbtBuildPromise, supportedBuildTasks.artifactBuildTasks)
      else
        sbtBuildPromise

    resultPromise
  }


  private final case class CollectedBuildTasks(
    moduleBuildTasks: Seq[ModuleBuildTask],
    artifactBuildTasks: Seq[(ProjectModelBuildTask[?], Artifact)],
  )

  private def collectSupportedBuildTasks(tasks: Seq[ProjectTask]): CollectedBuildTasks = {
    val moduleBuildTasks = mutable.ArrayBuffer.empty[ModuleBuildTask]
    val artifactBuildTasks = mutable.ArrayBuffer.empty[(ProjectModelBuildTask[?], Artifact)]

    // TODO Android AARs are currently imported as modules. need a way to filter them away before building
    // (NOTE: this comment is form ancient time, not sure how actual it still is)
    tasks.foreach {
      case moduleTask: ModuleBuildTask =>
        // Filter out modules representing sbt meta-build projects (`-build` modules)
        if (!isBuildModule(moduleTask)) {
          moduleBuildTasks += moduleTask
        }
      case artifactTask: ProjectModelBuildTask[_] =>
        // "Build Artifact":
        // Handle the case when the project build is invoked transitively via "Build Artifact" action
        // It can happen when:
        //  - A user invokes "Build Artifact" from the context menu of an artifact in the Artifacts view
        //  - Build Artifact is used in the "Before launch" step of a "Run Configuration".
        //    This is mostly actual in the Scala Plugin repo when sbt-shell is used and we run ide using scalaUltimate/scalaCommunity run configuration
        // ATTENTION: This code should be in sync with the code inside `canRun` and `run` methods
        artifactTask.getBuildableElement match {
          case artifact: Artifact =>
            artifactBuildTasks += (artifactTask -> artifact)
          case _ =>
        }
      case _: ExecuteRunConfigurationTask =>
        // TODO this includes tests (and what else?). sbt should handle it and test output should be parsed
        //  (NOTE: this comment is originally form 2016)
        false
      case _ =>
    }

    CollectedBuildTasks(
      moduleBuildTasks = moduleBuildTasks.toSeq,
      artifactBuildTasks = artifactBuildTasks.toSeq,
    )
  }

  private def extractModulesFromBuildTasks(
    project: Project,
    collected: CollectedBuildTasks
  ): Seq[Module] = {
    val buildTasksModules = collected.moduleBuildTasks.map(_.getModule)

    val artifacts = collected.artifactBuildTasks.map(_._2)
    val artifactBuildTasksModules =  artifacts.flatMap(getModulesIncludedInArtifact(project, _))
    buildTasksModules ++ artifactBuildTasksModules
  }

  private def getModulesIncludedInArtifact(project: Project, artifact: Artifact): Set[Module] =
    ArtifactUtil.getModulesIncludedInArtifacts(util.Arrays.asList(artifact), project).asScala.toSet

  private def isBuildModule(task: ModuleBuildTask): Boolean = {
    val moduleType = ModuleType.get(task.getModule)
    moduleType.getId == SbtModuleType.Id
  }

  private def runSbtBuildTasks(
    project: Project,
    supportedBuildTasks: Seq[BuildTask],
    sbtBuildCommands: Seq[String]
  ): AsyncPromise[ProjectTaskRunner.Result] = {
    Log.debug(s"runSbtBuildTasks start: supportedTasks=${supportedBuildTasks.size}, sbtCommands=${sbtBuildCommands.size}...")
    if (Log.isTraceEnabled) {
      Log.trace(s"runSbtBuildTasks: sbtCommands=${sbtBuildCommands.mkString(", ")}")
    }

    if (sbtBuildCommands.isEmpty) {
      // don't run anything if there's no module to run a build for
      if (supportedBuildTasks.nonEmpty) {
        showNotificationThatExternalSystemHasLostModuleData(project)
      }

      Log.debug("runSbtBuildTasks finishL: no sbt commands, returning successful done-promise")

      createDonePromise(TaskRunnerResult(isAborted = false, hasErrors = false))
    } else {
      val promiseResult = new AsyncPromise[ProjectTaskRunner.Result]()

      // Ensure all documents are saved to disk before running the SBT compilation
      invokeAndWait {
        FileDocumentManager.getInstance().saveAllDocuments()
      }

      val commandFinal: String =
        if (sbtBuildCommands.size == 1) sbtBuildCommands.head
        else sbtBuildCommands.mkString("all ", " ", "")

      // run this as a task (which blocks a thread) because it seems non-trivial to just update indicators asynchronously?
      val task = new CommandTask(project, commandFinal, promiseResult)
      ProgressManager.getInstance().run(task)

      promiseResult
    }
  }

  //noinspection UsagesOfObsoleteApi
  private def createDonePromise(result: TaskRunnerResult): AsyncPromise[ProjectTaskRunner.Result] = {
    val promise = new AsyncPromise[ProjectTaskRunner.Result]()
    promise.setResult(result)
    promise
  }

  private def getOrDetectSbtVersion(project: Project): SbtVersion =
    SbtProcessManager.instanceIfCreated(project)
      .flatMap(_.sbtVersionUsedDuringProcessStart)
      .getOrElse(SbtVersionDetector.detectSbtVersion(project))

  /**
   * Sometimes the external system loses information about sbt modules.
   * It would be very confusing to users if a build task silently did nothing.
   * We detect such cases and suggest project refresh.
   *
   * @see SCL-15118
   * @todo Add information on how actual it still is in 2026?
   *       What are potential reasons the eternal system information is lost?
   */
  private def showNotificationThatExternalSystemHasLostModuleData(project: Project): Unit = {
    val notification = ScalaNotificationGroups.sbtShell.createNotification(
      SbtBundle.message("sbt.shell.sbt.build.failed"),
      SbtBundle.message("sbt.shell.unable.to.build.sbt.project", project.getName),
      NotificationType.ERROR
    )

    notification.addAction(
      NotificationAction.createSimple(
        SbtBundle.message("sbt.shell.refresh.sbt.project"),
        (() => ExternalSystemUtil.refreshProjects(new ImportSpecBuilder(project, SbtProjectSystem.Id))): Runnable
      )
    )

    notification.notify(project)
  }

  @Nullable
  override def createExecutionEnvironment(project: Project,
                                          task: ExecuteRunConfigurationTask,
                                          executor: Executor): ExecutionEnvironment = {

    val taskSettings = new ExternalSystemTaskExecutionSettings
    val executorId = Option(executor).map(_.getId).getOrElse(DefaultRunExecutor.EXECUTOR_ID)

    ExternalSystemUtil.createExecutionEnvironment(
      project,
      SbtProjectSystem.Id,
      taskSettings, executorId
    )
  }

}

// TODO: PerformInBackgroundOption is deprecated, ProgressManager.run(Task) is obsolete. See IJPL-384
private class CommandTask(
  project: Project,
  command: String,
  projectTaskPromise:
  AsyncPromise[ProjectTaskRunner.Result]
) extends Task.Backgroundable(project, SbtBundle.message("sbt.shell.sbt.build"), true) {
  private val log = Logger.getInstance(getClass)

  private val resultPromise: Promise[BuildMessages] = Promise()

  override def onThrowable(error: Throwable): Unit =
    resultPromise.failure(error)

  override def onCancel(): Unit =
    resultPromise.tryFailure(new ProcessCanceledException())

  override def run(indicator: ProgressIndicator): Unit = {
    import org.jetbrains.plugins.scala.lang.macros.expansion.ReflectExpansionsCollector

    log.debug(s"run start: project=${project.getName}, command=$command")

    val buildId = BuildMessages.randomEventId
    val report = new CompositeReporter(
      // Set `activateToolWindowWhenFailed` to `false` to prevent jumping to the build tool window and causing distractions when the build fails
      new BuildToolWindowReporter(
        project,
        buildId,
        SbtBundle.message("sbt.shell.sbt.build"),
        new CancelBuildAction(resultPromise, indicator = None),
        activateToolWindowWhenFailed = false,
        activateToolWindowWhenWarned = true
      ),
      new IndicatorReporter(indicator)
    )

    val shell = SbtShellCommunication.forProject(project)
    val collector = ReflectExpansionsCollector.getInstance(project)

    log.trace(s"run: build reporter + collector start: buildId=$buildId...")

    report.start()
    collector.compilationStarted()

    // Currently, the entire build output is printed in the root node of the build window.
    // As a potential improvement, this could be moved to a separate node.
    val resultAggregator = SbtShellBuildMessagesEventProcessor.forBuild(
      project,
      report,
      buildId,
      processOutputCollector = None,
      startMessage = SbtBundle.message("sbt.shell.sbt.build"),
      finishMessage = SbtBundle.message("sbt.shell.sbt.build.finished"),
      onOutputLine = text => collector.processCompilerMessage(text)
    )

    // TODO consider running module build tasks separately
    // may require collecting results individually and aggregating
    val terminationMessage = "Sbt shell terminated before build command is finished"
    val request = SbtShellCommandRequest(command, resultAggregator, Some(terminationMessage))
      .withQueuedOutputMirroring()
    val requestId = request.requestId

    log.trace(s"run: shell.command enqueue start: requestId=$requestId...")

    val commandFuture: Future[BuildMessages] = shell.run(request)

    log.trace(s"run: shell.command enqueue finish: requestId=$requestId")

    log.trace(s"run: waitForCancelable start: requestId=$requestId...")

    // block thread to make indicator available :(
    val buildMessages = CancelableWaitUtil.waitForCancelable(
      commandFuture,
      onCancel = () => shell.removeCommandFromQueueOrCancel(requestId)
    )(resultPromise, indicator)

    log.trace(s"run: waitForCancelable finish: requestId=$requestId, isSuccess=${buildMessages.isSuccess}")

    // handle callback
    buildMessages match {
      case Success(messages) =>
        log.trace(s"run: result success: status=${messages.status}, errors=${messages.errors.size}, warnings=${messages.warnings.size}")

        val taskResult = messages.toTaskRunnerResult
        projectTaskPromise.setResult(taskResult)
      case Failure(x) =>
        log.trace(s"run: result failure: ${x.getClass.getName}: ${x.getMessage}")

        projectTaskPromise.setError(x)
    }

    // build state reporting
    // TODO: Improve handling of canceled builds.
    //  Most cancellation scenarios are currently reported as "failed".
    //  The only exception is when the build command is still in the shell queue (not yet started) and the shell is killed.
    buildMessages match {
      case Success(messages) =>
        log.trace(s"run: report.finish")
        report.finish(messages)

      case Failure(err) =>
        log.trace(s"run: report.finishWithFailure")
        report.finishWithFailure(err)
    }

    // build effects
    try {
      log.trace(s"run: refreshRoots")
      ExternalSystemVfsUtil.refreshRoots(project, SbtProjectSystem.Id, indicator)
    } catch {
      // Suppress the `ProcessCanceledException` that might be thrown by #refreshRoots to ensure the code below runs even if the build is canceled.
      // Currently, cancellation that stops the indicator and may cause `ProcessCanceledException` can be done by clicking the "stop" button in the build tool window.
      // Once SCL-24358 is implemented, this will also apply when the build is canceled directly from the progress indicator.
      // TODO: investigate whether the code below is still necessary when the build is canceled.
      //  I added this suppression because it worked like this in the past (e.g., when the build was canceled by killing the sbt shell).
      case _: ProcessCanceledException =>
        log.trace(s"run: refreshRoots canceled (ProcessCanceledException)")
    }

    // reload changed classes
    val debuggerSession = DebuggerManagerEx.getInstanceEx(project).getContext.getDebuggerSession
    val debuggerSettings = DebuggerSettings.getInstance
    if (debuggerSession != null &&
      debuggerSession.isAttached &&
      debuggerSettings.RUN_HOTSWAP_AFTER_COMPILE == DebuggerSettings.RUN_HOTSWAP_ALWAYS) {
      extensions.invokeLater {
        HotSwapUI.getInstance(project).reloadChangedClasses(debuggerSession, false)
      }
    }
    collector.compilationFinished()

    resultPromise.trySuccess(buildMessages.get)

    log.debug(s"run finish: project=${project.getName}, command=$command")
  }
}
