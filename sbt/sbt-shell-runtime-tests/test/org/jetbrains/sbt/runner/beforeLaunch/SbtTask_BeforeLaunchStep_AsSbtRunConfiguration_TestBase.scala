package org.jetbrains.sbt.runner.beforeLaunch

import com.intellij.compiler.options.CompileStepBeforeRun
import com.intellij.debugger.impl.GenericDebuggerRunner
import com.intellij.execution.application.{ApplicationConfiguration, ApplicationConfigurationType}
import com.intellij.execution.impl.DefaultJavaProgramRunner
import com.intellij.execution.impl.RunManagerImpl
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.{ExecutionManager, RunnerAndConfigurationSettings}
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.task.{ProjectTaskContext, ProjectTaskListener, ProjectTaskManager}
import com.intellij.testFramework.{PlatformTestUtil, VfsTestUtil}
import org.jetbrains.plugins.scala.build.BuildDiagnosticsCollector
import org.jetbrains.plugins.scala.ui.AwaitTestUtils
import org.jetbrains.sbt.runner.TestExecutionOptions.ExecutionMode
import org.jetbrains.sbt.runner.beforeLaunch.SbtTask_BeforeLaunchStep_AsSbtRunConfiguration_TestBase.*
import org.jetbrains.sbt.runner.beforeLaunch.utils.DebuggerSessionsAwaiter
import org.jetbrains.sbt.runner.beforeLaunch.utils.RunConfigurationBeforeLaunchTaskTestUtil
import org.jetbrains.sbt.runner.utils.ExecutionEventsCollector.ExecutionEvent
import org.jetbrains.sbt.runner.utils.{ExecutionEventsCollector, RunConfigInTestsExecutor, RunConfigurationExecutionObserver, SbtRunConfigurationTestFactory}
import org.jetbrains.sbt.runner.{SbtDebugProgramRunner, SbtProgramRunner, SbtRunConfiguration_MockedProcess_ExecutionTestBase, TestExecutionOptions}
import org.junit.Assert.{assertEquals, assertFalse, assertNotNull, assertSame, assertTrue, fail}

import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.{AtomicInteger, AtomicReference}
import scala.concurrent.duration.{Duration, DurationInt}

/**
 * Shared support for SBT task run configurations attached through the "Run another configuration before launch" provider.
 *
 * The checks stay focused on nested execution: runner delegation, execution listener notifications, and ordering between
 * the nested SBT task and the dependent configuration. Build / Make before-launch assertions live in
 * `SbtRunConfiguration_BuildBeforeLaunchTest`.
 *
 * Regression coverage:
 *  - [[https://youtrack.jetbrains.com/issue/SCL-24434 SCL-24434]]
 *  - [[https://youtrack.jetbrains.com/issue/SCL-24469 SCL-24469]]
 */
abstract class SbtTask_BeforeLaunchStep_AsSbtRunConfiguration_TestBase extends SbtRunConfiguration_MockedProcess_ExecutionTestBase {

  protected def assertSbtTaskAsBeforeLaunchStepOfAnotherConfigurationUsesExpectedRunnerDelegation(options: TestExecutionOptions): Unit = {
    val (_, handler) = runAndAssertSbtTaskAsBeforeLaunchStepOfDependentConfiguration(options)
    val usesSbtShellDelegation = handler.getClass.getName.contains("DummyProcessHandler")
    if (shouldUseSyntheticSbtShellProcessHandler(options)) {
      assertTrue("The nested SBT shell task must use the synthetic sbt-shell process handler", usesSbtShellDelegation)
    } else {
      assertFalse("The nested SBT task must use a regular process/debug handler", usesSbtShellDelegation)
    }
  }

  protected def assertSbtTaskAsBeforeLaunchStepOfAnotherConfigurationNotifiesExecutionListeners(options: TestExecutionOptions): Unit = {
    runAndAssertSbtTaskAsBeforeLaunchStepOfDependentConfiguration(options)
  }

  protected def assertSbtTaskAsBeforeLaunchStepOfAnotherConfigurationDoesNotBlockDependentRunConfiguration(options: TestExecutionOptions): Unit = {
    val (result, _) = runAndAssertSbtTaskAsBeforeLaunchStepOfDependentConfiguration(options)

    val sbtTaskTerminated = singleEvent(result.sbtTaskEvents, "processTerminated")
    val dependentConfigurationStarted = singleEvent(result.dependentConfigurationEvents, "processStarted")
    assertTrue(
      "The dependent configuration must start only after the nested SBT task terminates",
      sbtTaskTerminated.order < dependentConfigurationStarted.order
    )
  }

  private def runAndAssertSbtTaskAsBeforeLaunchStepOfDependentConfiguration(options: TestExecutionOptions): (BeforeLaunchExecutionResult, ProcessHandler) = {
    val result =
      try {
        runSbtTaskAsBeforeLaunchStepOfDependentConfiguration(options)
      } finally {
        tearDownForTestCase(options)
      }

    assertExpectedRunner(result.sbtTaskEvents, options)
    val handler = assertNestedSbtTaskLifecycle(result.sbtTaskEvents)

    (result, handler)
  }

  private def runSbtTaskAsBeforeLaunchStepOfDependentConfiguration(options: TestExecutionOptions): BeforeLaunchExecutionResult = {
    initSbtShellIfNeeded(options)

    val sbtTaskSettings = createNestedSbtTaskRunConfiguration(options)
    val dependentConfigurationSettings = createDependentApplicationRunConfiguration(options)
    RunConfigurationBeforeLaunchTaskTestUtil.addRunConfigurationBeforeLaunchTask(
      getProject,
      dependentConfigurationSettings,
      sbtTaskSettings,
    )

    val eventCounter = new AtomicInteger()
    val sbtTaskEventsCollector = new ExecutionEventsCollector(sbtTaskSettings, eventCounter)
    val dependentConfigurationEventsCollector = new ExecutionEventsCollector(dependentConfigurationSettings, eventCounter)

    val sbtTaskObserver = RunConfigurationExecutionObserver.subscribe(
      sbtTaskSettings,
      getTestRootDisposable,
      captureConsoleOutput = options.expectsRunConfigurationDebugConnection,
    )
    val dependentConfigurationObserver = RunConfigurationExecutionObserver.subscribe(
      dependentConfigurationSettings,
      getTestRootDisposable,
      captureConsoleOutput = false,
    )
    val debuggerSessionsAwaiter = observeDebuggerSessionsIfNeeded(options)

    val compileBeforeLaunchObserver = new CompileBeforeLaunchObserver
    val buildDiagnosticsCollector = BuildDiagnosticsCollector.start(getProject, getTestRootDisposable)

    val connection = getProject.getMessageBus.connect(getTestRootDisposable)
    connection.subscribe(ExecutionManager.EXECUTION_TOPIC, sbtTaskEventsCollector)
    connection.subscribe(ExecutionManager.EXECUTION_TOPIC, dependentConfigurationEventsCollector)
    connection.subscribe(ProjectTaskListener.TOPIC, compileBeforeLaunchObserver)

    clearSbtProcessOutputDiagnostics()
    RunConfigInTestsExecutor.executeTopLevelConfiguration(
      getProject,
      dependentConfigurationSettings,
      options.executionMode.executor,
    )
    compileBeforeLaunchObserver.awaitSuccessfulCompletion(
      timeout = 10.seconds,
      buildDiagnostics = buildDiagnosticsCollector.snapshot,
    )
    // With mock sbt we don't need to wait for a long time, 5 seconds is more than enough
    sbtTaskObserver.awaitSuccessfulTermination(timeout = 5.seconds)
    dependentConfigurationObserver.awaitSuccessfulTermination(timeout = 10.seconds)
    debuggerSessionsAwaiter.foreach(_.awaitAllSessionsDetached())
    assertExpectedDebugOutput(options, sbtTaskObserver)

    BeforeLaunchExecutionResult(
      sbtTaskEventsCollector.eventsSnapshot,
      dependentConfigurationEventsCollector.eventsSnapshot,
    )
  }

  private def shouldUseSyntheticSbtShellProcessHandler(options: TestExecutionOptions): Boolean =
    options.useSbtShellInRunConfig && (options.executionMode != ExecutionMode.Debug || !options.enableDebuggingInShell)

  private def observeDebuggerSessionsIfNeeded(options: TestExecutionOptions): Option[DebuggerSessionsAwaiter] =
    options.executionMode match {
      case ExecutionMode.Debug => Some(DebuggerSessionsAwaiter.subscribe(getProject, timeout = 10.seconds))
      case ExecutionMode.Run => None
    }

  private def assertExpectedRunner(events: Vector[ExecutionEvent], options: TestExecutionOptions): Unit = {
    val expected = expectedRunnerId(options)
    val actualRunnerIds = events.flatMap(_.runnerId).distinct
    assertEquals(
      s"Unexpected runner for nested SBT task. Actual runner ids: ${actualRunnerIds.mkString("[", ", ", "]")}",
      Seq(expected),
      actualRunnerIds,
    )
  }

  private def expectedRunnerId(options: TestExecutionOptions): String =
    (options.executionMode, options.useSbtShellInRunConfig) match {
      case (ExecutionMode.Run, true) => SbtProgramRunnerId
      case (ExecutionMode.Debug, true) => SbtDebugProgramRunnerId
      case (ExecutionMode.Run, false) => DefaultRunRunnerId
      case (ExecutionMode.Debug, false) => DefaultDebugRunnerId
    }

  private def createNestedSbtTaskRunConfiguration(options: TestExecutionOptions): RunnerAndConfigurationSettings = {
    val settings = SbtRunConfigurationTestFactory.createNewSbtTaskRunConfiguration(
      getProject,
      s"sbt compile before ${options.executionMode.displayName} (${sbtShellModeDisplayName(options)})",
      "compile",
      options.useSbtShellInRunConfig,
    )
    RunManagerImpl.getInstanceImpl(getProject).addConfiguration(settings)
    settings
  }

  private def createDependentApplicationRunConfiguration(options: TestExecutionOptions): RunnerAndConfigurationSettings = {
    ensureDependentApplicationMainClassExists()

    val runManager = RunManagerImpl.getInstanceImpl(getProject)
    val factory = ApplicationConfigurationType.getInstance().getConfigurationFactories()(0)
    val settings = runManager.createConfiguration(
      s"dependent ${options.executionMode.displayName.toLowerCase} application",
      factory,
    )
    val configuration = settings.getConfiguration.asInstanceOf[ApplicationConfiguration]
    configuration.setMainClassName(DependentMainClassName)
    configuration.getConfigurationModule.setModule(getModule)
    configuration.setWorkingDirectory(getProject.getBasePath)
    runManager.addConfiguration(settings)
    settings
  }

  private def ensureDependentApplicationMainClassExists(): Unit = {
    VfsTestUtil.createFile(
      sourceRoot,
      s"$DependentMainClassName.java",
      s"""public final class $DependentMainClassName {
         |  public static void main(String[] args) {
         |    System.out.println("$DependentMainClassName started");
         |  }
         |}
         |""".stripMargin
    )
  }

  private def sourceRoot: VirtualFile = {
    val root = PlatformTestUtil.getOrCreateProjectBaseDir(getProject).findChild("src")
    assertNotNull("The test project source root must be created during setUp", root)
    root
  }

  private def assertNestedSbtTaskLifecycle(events: Vector[ExecutionEvent]): ProcessHandler = {
    assertEventsInOrder(
      events,
      Seq("processStarting", "processStartingWithHandler", "processStarted", "processTerminating", "processTerminated"),
      "The nested SBT task must publish a complete execution lifecycle",
    )
    assertAllHandlerSpecificEventsUseSameHandler(events, "The nested SBT task lifecycle must use one process handler")
  }

  private def assertAllHandlerSpecificEventsUseSameHandler(events: Vector[ExecutionEvent], message: String): ProcessHandler = {
    val handlers = events.flatMap(_.processHandler)
    assertTrue(s"$message: no handler-specific events were published. Actual events: ${eventNames(events)}", handlers.nonEmpty)
    val firstHandler = handlers.head
    handlers.foreach { handler =>
      assertSame(message, firstHandler, handler)
    }
    firstHandler
  }

  private def assertEventsInOrder(events: Vector[ExecutionEvent], expectedEventNames: Seq[String], message: String): Unit = {
    var lastOrder = Int.MinValue
    expectedEventNames.foreach { eventName =>
      val event = events.find(event => event.name == eventName && event.order > lastOrder)
      assertTrue(
        s"$message: expected '$eventName' after order $lastOrder. Actual events: ${eventNames(events)}",
        event.isDefined,
      )
      lastOrder = event.get.order
    }
  }

  private def singleEvent(events: Vector[ExecutionEvent], eventName: String): ExecutionEvent = {
    val matchingEvents = events.filter(_.name == eventName)
    assertEquals(s"Expected exactly one '$eventName' event. Actual events: ${eventNames(events)}", 1, matchingEvents.size)
    matchingEvents.head
  }

  private def eventNames(events: Vector[ExecutionEvent]): String =
    events.map(event => s"${event.order}:${event.name}").mkString("[", ", ", "]")

  private final case class BeforeLaunchExecutionResult(
    sbtTaskEvents: Vector[ExecutionEvent],
    dependentConfigurationEvents: Vector[ExecutionEvent],
  )

  private final class CompileBeforeLaunchObserver extends ProjectTaskListener {

    private val taskFinished = new CountDownLatch(1)
    private val taskResult = new AtomicReference[ProjectTaskManager.Result]()

    override def finished(result: ProjectTaskManager.Result): Unit = {
      if (isCompileBeforeLaunchContext(result.getContext)) {
        taskResult.set(result)
        taskFinished.countDown()
      }
    }

    def awaitSuccessfulCompletion(
      timeout: Duration,
      buildDiagnostics: => BuildDiagnosticsCollector.Snapshot,
    ): Unit = {
      AwaitTestUtils.waitForLatchDispatchingAllEdtEvents(
        taskFinished,
        timeout,
        "Timed out waiting for dependent Application Make before-launch step to finish",
      )

      val result = taskResult.get()
      if (result == null) {
        fail(s"Dependent Application Make before-launch step finished without a ProjectTaskManager result.${buildDiagnostics.rendered}")
      }
      val resultDescription = s"aborted=${result.isAborted}, hasErrors=${result.hasErrors}"
      if (result.isAborted) {
        fail(s"Dependent Application Make before-launch step must not be aborted ($resultDescription).${buildDiagnostics.rendered}")
      }
      if (result.hasErrors) {
        fail(s"Dependent Application Make before-launch step must finish without errors before the nested SBT task can start ($resultDescription).${buildDiagnostics.rendered}")
      }
    }

    private def isCompileBeforeLaunchContext(context: ProjectTaskContext): Boolean =
      context.getBuildOriginatorClass == classOf[CompileStepBeforeRun]
  }

  private val DependentMainClassName = "SbtBeforeLaunchDependentMain"
}

private object SbtTask_BeforeLaunchStep_AsSbtRunConfiguration_TestBase {
  private val SbtProgramRunnerId: String = new SbtProgramRunner().getRunnerId
  private val SbtDebugProgramRunnerId: String = new SbtDebugProgramRunner().getRunnerId
  private val DefaultRunRunnerId: String = new DefaultJavaProgramRunner().getRunnerId
  private val DefaultDebugRunnerId: String = new GenericDebuggerRunner().getRunnerId
}
