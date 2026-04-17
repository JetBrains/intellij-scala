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
import org.jetbrains.sbt.SbtSourceSetUtil.SbtSourceSetModuleExt
import org.jetbrains.sbt.project.SbtProjectSystem
import org.jetbrains.sbt.project.data.SbtModuleData
import org.jetbrains.sbt.project.module.SbtModuleType
import org.jetbrains.sbt.settings.SbtSettings
import org.jetbrains.sbt.{SbtBundle, SbtUtil, SbtVersion, SbtVersionCapabilities, SbtVersionDetector}

import java.util
import java.util.UUID
import scala.collection.mutable
import scala.concurrent.{Future, Promise}
import scala.jdk.CollectionConverters.SetHasAsScala
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
//noinspection UsagesOfObsoleteApi (the API is used by the base class)
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
    if (hasSupportedTasks) {
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
      false
    }
  }

  private def isUseSbtShellForBuildEnabled(module: Module): Boolean = {
    val project = module.getProject

    val sbtProjectSettings = SbtSettings.getInstance(project).getLinkedProjectSettings(module)

    sbtProjectSettings.exists(_.useSbtShellForBuild) &&
      ES.isExternalSystemAwareModule(SbtProjectSystem.Id, module)
  }

  /** Represents the sbt source set scope (main or test) for which a build command should be generated. */
  private enum SbtScope:
    case Main
    case Test

  override def run(
    project: Project,
    context: ProjectTaskContext,
    tasks: ProjectTask*
  ): IJPromise[ProjectTaskRunner.Result] = {
    val supportedBuildTasks = collectSupportedBuildTasks(tasks)

    val buildTasks: Seq[BuildTask] =
      supportedBuildTasks.moduleBuildTasks ++
        supportedBuildTasks.artifactBuildTasks.map(_._1)
    val taskModules = extractModulesFromBuildTasks(project, supportedBuildTasks)
    val scopesPerModule: Map[SbtModuleData, Set[SbtScope]] =
      groupTasksPerModule(taskModules)

    // The "build" button in IDEA always runs the build for all individual modules.
    // This may work differently than just calling the "products" sbt task from the main module in sbt
    val sbtBuildCommands: Seq[String] =
      buildSbtBuildCommands(project, scopesPerModule)
    Log.debug(s"Running delegated sbt build: supportedTasks=${buildTasks.size}, artifactTasks=${supportedBuildTasks.artifactBuildTasks.size}, sbt commands=${sbtBuildCommands.size}")

    val sbtBuildPromise = runSbtBuildTasks(project, buildTasks, sbtBuildCommands)
    val needToBuildArtifactAfter = supportedBuildTasks.artifactBuildTasks.nonEmpty
    if (needToBuildArtifactAfter)
      // Unlike plain JPS runner, sbt compile is a separate async phase here.
      // Artifact packaging is chained explicitly and starts only after successful sbt completion.
      SbtJpsArtifactPackagingUtil.chainSbtBuildAndJpsArtifactPackaging(project, context, sbtBuildPromise, supportedBuildTasks.artifactBuildTasks)
    else
      sbtBuildPromise
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
    if (sbtBuildCommands.isEmpty) {
      // don't run anything if there's no module to run a build for
      if (supportedBuildTasks.nonEmpty) {
        showNotificationThatExternalSystemHasLostModuleData(project)
      }

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

  private def createDonePromise(result: TaskRunnerResult): AsyncPromise[ProjectTaskRunner.Result] = {
    val promise = new AsyncPromise[ProjectTaskRunner.Result]()
    promise.setResult(result)
    promise
  }

  /**
   * Collect the sbt scopes (main/test) per sbt module.<br>
   * This is done to:
   *  1. Avoid duplicate commands:<br>
   *     Triggering "Build project" for a project with a single sbt module results in 3 ProjectTasks:
   *     1. for the parent module
   *     2. for the main module
   *     3. for the test module
   *
   *     For our use case, only 2 "products" commands are needed:
   *     1. one for Compile scope in the given sbt module
   *     2. one for Test scope in the given sbt module
   *
   * The logic in this method ensures duplicates are filtered out.
   *  2. Run the "products" task only in the relevant scope:<br>
   *     When a build is triggered for a main or test module, only the "products" task in the Compile or Test scope (respectively) should be executed.
   */
  private def groupTasksPerModule(modules: Seq[Module]): Map[SbtModuleData, Set[SbtScope]] = {
    val acc = mutable.Map.empty[SbtModuleData, Set[SbtScope]].withDefaultValue(Set.empty)

    for
      module <- modules
      data <- SbtUtil.getSbtModuleData(module)
    do
      val newScopes = moduleScopes(module)
      val existingScopes = acc(data)
      acc.update(data, existingScopes ++ newScopes)

    acc.toMap
  }

  private def moduleScopes(module: Module): Set[SbtScope] =
    if !module.isSbtSourceSetModule then Set(SbtScope.Main, SbtScope.Test)
    else if module.isMain then Set(SbtScope.Main)
    else Set(SbtScope.Test)

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

  /**
   * Builds the list of sbt shell commands for the given modules and their scopes.
   *
   * @todo sensible way to find out what scopes to run it for besides compile and test?
   * @todo make tasks should be user-configurable
   */
  private def buildSbtBuildCommands(
    project: Project,
    scopesPerModule: Map[SbtModuleData, Set[SbtScope]]
  ): Seq[String] = {
    val sbtVersion: SbtVersion = getOrDetectSbtVersion(project)
    scopesPerModule.toSeq.flatMap { case (sbtModuleData, scopes) =>
      val projectScope = SbtUtil.makeSbtProjectId(sbtModuleData)
      // `products` task is a little more general than just `compile`
      scopes.map {
        case SbtScope.Main =>
          s"$projectScope/products"
        case SbtScope.Test =>
          if SbtVersionCapabilities.isSlashSyntaxSupported(sbtVersion) then
            s"$projectScope/Test/products"
          else
            s"$projectScope/test:products"
      }
    }
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

  private val resultPromise: Promise[BuildMessages] = Promise()

  override def onThrowable(error: Throwable): Unit =
    resultPromise.failure(error)

  override def onCancel(): Unit =
    resultPromise.tryFailure(new ProcessCanceledException())

  override def run(indicator: ProgressIndicator): Unit = {
    import org.jetbrains.plugins.scala.lang.macros.expansion.ReflectExpansionsCollector

    val buildId = BuildMessages.randomEventId
    val report = new CompositeReporter(
      // Set `activateToolWindowWhenFailed` to false to prevent jumping to the build tool window and causing distractions when the build fails
      new BuildToolWindowReporter(project, buildId, SbtBundle.message("sbt.shell.sbt.build"), new CancelBuildAction(resultPromise, indicator = None), activateToolWindowWhenFailed = false),
      new IndicatorReporter(indicator)
    )

    val shell = SbtShellCommunication.forProject(project)
    val collector = ReflectExpansionsCollector.getInstance(project)

    report.start()
    collector.compilationStarted()

    // Currently, the entire build output is printed in the root node of the build window.
    // As a potential improvement, this could be moved to a separate node.
    val resultAggregator = shell.messageAggregatorForBuild(
      report,
      buildId,
      processOutputBuilder = None,
      startMessage = SbtBundle.message("sbt.shell.sbt.build"),
      finishMessage = SbtBundle.message("sbt.shell.sbt.build.finished"),
      onOutputLine = text => collector.processCompilerMessage(text)
    )
    
    // TODO consider running module build tasks separately
    // may require collecting results individually and aggregating
    val id = UUID.randomUUID().toString
    val terminationMessage = "Sbt shell terminated before build command is finished"
    val commandFuture: Future[BuildMessages] = shell.command(command, id, BuildMessages.empty, resultAggregator, Some(terminationMessage))

    // block thread to make indicator available :(
    val buildMessages = CancelableWaitUtil.waitForCancelable(
      commandFuture,
      onCancel = () => shell.removeCommandFromQueueOrCancel(id)
    )(resultPromise, indicator)

    // handle callback
    buildMessages match {
      case Success(messages) =>
        val taskResult = messages.toTaskRunnerResult
        projectTaskPromise.setResult(taskResult)
      case Failure(x) =>
        projectTaskPromise.setError(x)
    }

    // build state reporting
    // TODO: Improve handling of canceled builds.
    //  Most cancellation scenarios are currently reported as "failed".
    //  The only exception is when the build command is still in the shell queue (not yet started) and the shell is killed.
    buildMessages match {
      case Success(messages) => report.finish(messages)
      case Failure(err) => report.finishWithFailure(err)
    }

    // build effects
    try {
      ExternalSystemVfsUtil.refreshRoots(project, SbtProjectSystem.Id, indicator)
    } catch {
      // Suppress the `ProcessCanceledException` that might be thrown by #refreshRoots to ensure the code below runs even if the build is canceled.
      // Currently, cancellation that stops the indicator and may cause `ProcessCanceledException` can be done by clicking the "stop" button in the build tool window.
      // Once SCL-24358 is implemented, this will also apply when the build is canceled directly from the progress indicator.
      // TODO: investigate whether the code below is still necessary when the build is canceled.
      //  I added this suppression because it worked like this in the past (e.g., when the build was canceled by killing the sbt shell).
      case _: ProcessCanceledException =>
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
  }
}
