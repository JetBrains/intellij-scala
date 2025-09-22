package org.jetbrains.sbt.shell

import com.intellij.debugger.DebuggerManagerEx
import com.intellij.debugger.settings.DebuggerSettings
import com.intellij.debugger.ui.HotSwapUI
import com.intellij.execution.Executor
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.notification.{NotificationAction, NotificationType}
import com.intellij.openapi.externalSystem.importing.ImportSpecBuilder
import org.jetbrains.plugins.scala.build.BuildToolWindowReporter
import com.intellij.openapi.externalSystem.model.execution.ExternalSystemTaskExecutionSettings
import com.intellij.openapi.externalSystem.util.{ExternalSystemUtil, ExternalSystemApiUtil => ES}
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleType
import com.intellij.openapi.progress.{PerformInBackgroundOption, ProcessCanceledException, ProgressIndicator, ProgressManager, Task}
import com.intellij.openapi.project.Project
import com.intellij.task._
import org.jetbrains.annotations.Nullable
import org.jetbrains.concurrency.{AsyncPromise, Promise => JPromise}
import org.jetbrains.plugins.scala.build.BuildToolWindowReporter.CancelBuildAction
import org.jetbrains.plugins.scala.build.{BuildMessages, CompositeReporter, IndicatorReporter, TaskRunnerResult}
import org.jetbrains.plugins.scala.extensions
import org.jetbrains.plugins.scala.util.{ExternalSystemVfsUtil, ScalaNotificationGroups}
import org.jetbrains.sbt.project.SbtProjectSystem
import org.jetbrains.sbt.project.module.SbtModuleType
import org.jetbrains.sbt.settings.SbtSettings
import org.jetbrains.sbt.{SbtBundle, SbtUtil, SbtVersion, SbtVersionCapabilities, SbtVersionDetector}

import java.util.UUID
import scala.annotation.nowarn
import scala.concurrent.{Future, Promise}
import scala.util.{Failure, Success}

final class SbtProjectTaskRunnerImpl
  extends ProjectTaskRunner
    with SbtProjectTaskRunner {

  // will override the usual jps build thingies
  override def canRun(projectTask: ProjectTask): Boolean = projectTask match {
    case task: ModuleBuildTask =>
      isUseSbtShellForBuildEnabled(task.getModule)
    case _: ExecuteRunConfigurationTask =>
      // TODO this includes tests (and what else?). sbt should handle it and test output should be parsed
      false
    case _ =>
      false
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
  ): JPromise[ProjectTaskRunner.Result] = {
    val validTasks = tasks.collect {
      // TODO Android AARs are currently imported as modules. need a way to filter them away before building
      case task: ModuleBuildTask
        // SbtModuleType actually denotes `-build` modules, which are not part of the regular build
        if ModuleType.get(task.getModule).getId != SbtModuleType.Id =>
          task
    }

    val sbtVersion: SbtVersion = SbtProcessManager.instanceIfCreated(project)
      .flatMap(_.sbtVersionUsedDuringProcessStart)
      .getOrElse(SbtVersionDetector.detectSbtVersion(project))

    // the "build" button in IDEA always runs the build for all individual modules,
    // and may work differently than just calling the products task from the main module in sbt
    val moduleCommands = validTasks.flatMap(buildCommands(_, sbtVersion))

    if (moduleCommands.isEmpty && validTasks.nonEmpty) {
      // sometimes external system loses information about sbt modules
      // since it is very confusing to users, when build task silently does nothing
      // we detect such cases and suggest project refresh
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

    val promiseResult = new AsyncPromise[ProjectTaskRunner.Result]()

    // don't run anything if there's no module to run a build for
    // TODO user feedback
    if (moduleCommands.isEmpty){
      val result = TaskRunnerResult(isAborted = false, hasErrors = false)
      promiseResult.setResult(result)
    } else {

      val command =
        if (moduleCommands.size == 1) moduleCommands.head
        else moduleCommands.mkString("all ", " ", "")

      extensions.invokeAndWait {
        FileDocumentManager.getInstance().saveAllDocuments()
      }

      // run this as a task (which blocks a thread) because it seems non-trivial to just update indicators asynchronously?
      val task = new CommandTask(project, command, promiseResult)
      ProgressManager.getInstance().run(task)
    }

    promiseResult
  }

  private def buildCommands(task: ModuleBuildTask, sbtVersion: SbtVersion): Seq[String] = {
    // TODO sensible way to find out what scopes to run it for besides compile and test?
    // TODO make tasks should be user-configurable
    SbtUtil.getSbtModuleData(task.getModule).toSeq.flatMap { sbtModuleData =>
      val projectScope = SbtUtil.makeSbtProjectId(sbtModuleData)
      // `products` task is a little more general than just `compile`
      val buildMain = s"$projectScope/products"
      val buildTest = if (SbtVersionCapabilities.isSlashSyntaxSupported(sbtVersion))
        s"$projectScope/Test/products"
      else
        s"$projectScope/test:products"
      Seq(buildMain, buildTest)
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
private class CommandTask(project: Project, command: String, projectTaskPromise: AsyncPromise[ProjectTaskRunner.Result]) extends
  Task.Backgroundable(project, SbtBundle.message("sbt.shell.sbt.build"), false, PerformInBackgroundOption.ALWAYS_BACKGROUND: @nowarn("cat=deprecation")) {

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
      new BuildToolWindowReporter(project, buildId, SbtBundle.message("sbt.shell.sbt.build"), new CancelBuildAction(resultPromise), activateToolWindowWhenFailed = false),
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
    val commandFuture: Future[BuildMessages] = shell.command(command, id, BuildMessages.empty, resultAggregator)

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