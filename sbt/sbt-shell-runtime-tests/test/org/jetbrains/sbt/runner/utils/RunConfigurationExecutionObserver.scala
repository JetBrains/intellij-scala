package org.jetbrains.sbt.runner.utils

import com.intellij.execution.process.{ProcessEvent, ProcessHandler, ProcessListener}
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.{ExecutionListener, ExecutionManager, RunnerAndConfigurationSettings}
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Key
import org.jetbrains.plugins.scala.ui.AwaitTestUtils
import org.jetbrains.sbt.process.SbtProcessOutputDiagnosticsCollector
import org.junit.Assert.{assertEquals, fail}

import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.{AtomicInteger, AtomicReference}
import scala.concurrent.duration.{DurationInt, FiniteDuration}

private[runner] final class RunConfigurationExecutionObserver(
  runConfigAndSettings: RunnerAndConfigurationSettings,
) extends ExecutionListener {

  private val configurationName = runConfigAndSettings.getName
  private val configurationNameQuoted = s"'$configurationName'"

  private val executionStarted = new CountDownLatch(1)
  private val executionFinished = new CountDownLatch(1)

  private final case class ProcessNotStarted(causeException: Option[Throwable])
  private val processNotStarted = new AtomicReference[ProcessNotStarted]()

  private val exitCode = new AtomicInteger(Int.MinValue)
  private val startedProcessHandler = new AtomicReference[ProcessHandler]()
  private val processOutput = new StringBuffer

  override def processStarting(executorId: String, env: ExecutionEnvironment, handler: ProcessHandler): Unit = {
    if (isObservedEnvironment(env)) {
      handler.addProcessListener(appendProcessOutputTo(processOutput))
    }
  }

  override def processStarted(executorId: String, env: ExecutionEnvironment, handler: ProcessHandler): Unit = {
    if (isObservedEnvironment(env)) {
      startedProcessHandler.compareAndSet(null, handler)
      executionStarted.countDown()
    }
  }

  override def processNotStarted(executorId: String, env: ExecutionEnvironment): Unit = {
    processNotStarted(env, null)
  }

  override def processNotStarted(executorId: String, env: ExecutionEnvironment, cause: Throwable): Unit = {
    processNotStarted(env, cause)
  }

  private def processNotStarted(env: ExecutionEnvironment, cause: Throwable): Unit = {
    if (isObservedEnvironment(env)) {
      processNotStarted.compareAndSet(null, ProcessNotStarted(Option(cause)))
      executionFinished.countDown()
    }
  }

  override def processTerminated(
    executorId: String,
    env: ExecutionEnvironment,
    handler: ProcessHandler,
    terminatedExitCode: Int,
  ): Unit = {
    if (isObservedEnvironment(env)) {
      exitCode.set(terminatedExitCode)
      executionFinished.countDown()
    }
  }

  def awaitSuccessfulTermination(timeout: FiniteDuration = 60.seconds): Unit = {
    awaitTermination(expectedExitCode = 0, timeout)
  }

  def awaitProcessStarted(timeout: FiniteDuration = 60.seconds): ProcessHandler = {
    try {
      AwaitTestUtils.waitForLatchDispatchingAllEdtEvents(
        executionStarted,
        timeout,
        s"Timed out waiting for $configurationNameQuoted execution to start",
        earlyBreakCondition = () => processNotStarted.get() != null,
      )
      failIfProcessNotStarted()

      val handler = startedProcessHandler.get()
      if (handler == null) {
        fail(s"$configurationNameQuoted published processStarted without a process handler")
      }
      handler
    } catch {
      case error: AssertionError =>
        printProcessOutputToStdErr()
        throw error
    }
  }

  def awaitTermination(expectedExitCode: Int, timeout: FiniteDuration = 60.seconds): Unit = {
    try {
      waitForExecutionFinished(timeout)
      failIfProcessNotStarted()
      assertEquals(s"$configurationNameQuoted should publish a processStarted event", 0L, executionStarted.getCount)
      assertEquals(s"$configurationNameQuoted should finish with exit code $expectedExitCode", expectedExitCode, exitCode.get())
    }
    catch {
      case error: AssertionError =>
        printProcessOutputToStdErr()
        throw error
    }
  }

  def awaitFailedToStart(expectedCauseMessage: String, timeout: FiniteDuration = 60.seconds): Unit = {
    try {
      waitForExecutionFinished(timeout)

      val notStarted = Option(processNotStarted.get()).getOrElse {
        fail(s"$configurationNameQuoted should fail before process start")
        null
      }
      val causeException = notStarted.causeException.getOrElse {
        fail(s"$configurationNameQuoted should fail before process start with an exception")
        null
      }
      assertEquals(
        s"$configurationNameQuoted failed before process start with unexpected exception message",
        expectedCauseMessage,
        causeException.getMessage,
      )
    } catch {
      case error: AssertionError =>
        printProcessOutputToStdErr()
        throw error
    }
  }

  def processOutputSnapshot: String =
    bufferText(processOutput)

  private def waitForExecutionFinished(timeout: FiniteDuration): Unit =
    AwaitTestUtils.waitForLatchDispatchingAllEdtEvents(
      executionFinished,
      timeout,
      s"Timed out waiting for $configurationNameQuoted execution to finish",
      earlyBreakCondition = () => processNotStarted.get() != null,
    )

  private def failIfProcessNotStarted(): Unit = {
    Option(processNotStarted.get()).foreach { notStarted =>
      val causeException = notStarted.causeException.orNull
      val failureMessage =
        if (causeException == null)
          s"$configurationNameQuoted should start successfully"
        else
          s"$configurationNameQuoted failed to start with an exception"
      if (causeException == null) {
        fail(failureMessage)
      } else {
        val error = new AssertionError(failureMessage)
        error.initCause(causeException)
        throw error
      }
    }
  }

  private def printProcessOutputToStdErr(): Unit = {
    printProcessOutput("Run configuration process output", bufferText(processOutput))
    printProcessOutput("SBT process output", SbtProcessOutputDiagnosticsCollector.sharedProcessOutput)
  }

  private def printProcessOutput(title: String, output: String): Unit = {
    val outputText = if (output.isEmpty) "<empty>" else output
    System.err.println(
      s"""$title:
         |$outputText""".stripMargin
    )
  }

  private def bufferText(output: StringBuffer): String =
    output.synchronized {
      output.toString
    }

  private def appendProcessOutputTo(output: StringBuffer): ProcessListener =
    new ProcessListener {
      override def onTextAvailable(event: ProcessEvent, outputType: Key[?]): Unit = {
        output.append(event.getText)
      }
    }

  private def isObservedEnvironment(env: ExecutionEnvironment): Boolean = {
    val environmentSettings = env.getRunnerAndConfigurationSettings
    environmentSettings != null && environmentSettings.getUniqueID == runConfigAndSettings.getUniqueID
  }
}

private[runner] object RunConfigurationExecutionObserver {

  def subscribe(
    runConfigAndSettings: RunnerAndConfigurationSettings,
    parentDisposable: Disposable,
  ): RunConfigurationExecutionObserver = {
    val executionObserver = new RunConfigurationExecutionObserver(runConfigAndSettings)
    val project = runConfigAndSettings.getConfiguration.getProject
    project.getMessageBus.connect(parentDisposable).subscribe(ExecutionManager.EXECUTION_TOPIC, executionObserver)
    executionObserver
  }
}
