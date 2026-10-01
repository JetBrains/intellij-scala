package org.jetbrains.plugins.scala.runner

import com.intellij.execution.configurations.RunnerSettings
import com.intellij.execution.process.{ProcessEvent, ProcessHandler, ProcessListener}
import com.intellij.execution.runners.{ExecutionEnvironment, ProgramRunner}
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer

import java.util.concurrent.CompletableFuture
import scala.util.control.NonFatal

/** Captures a test run's process and run content without blocking the dispatch thread. */
final class RunProcessTestSupport(
  parentDisposable: Disposable,
  listeners: Seq[ProcessListener] = Seq.empty,
  onStarted: RunContentDescriptor => Unit = _ => (),
) extends ProgramRunner.Callback {
  import RunProcessTestSupport._

  val startedFuture: CompletableFuture[StartedProcess] = new CompletableFuture[StartedProcess]()

  def execute(
    environment: ExecutionEnvironment,
    runner: ProgramRunner[? <: RunnerSettings],
  ): CompletableFuture[StartedProcess] = {
    //noinspection ApiStatus
    environment.setCallback(this)
    try runner.execute(environment)
    catch {
      case NonFatal(error) =>
        if (!startedFuture.completeExceptionally(error))
          throw error
    }
    startedFuture
  }

  override def processStarted(descriptor: RunContentDescriptor): Unit = {
    if (descriptor == null) {
      startedFuture.completeExceptionally(new AssertionError("No run content was created"))
      return
    }

    val handler = descriptor.getProcessHandler
    if (handler == null) {
      descriptor.dispose()
      startedFuture.completeExceptionally(new AssertionError("Run content has no process handler"))
      return
    }

    val cleanup = new ProcessCleanup(handler, descriptor)
    val exitListener = new ExitCodeListener
    try {
      Disposer.register(parentDisposable, cleanup)
      handler.addProcessListener(exitListener)
      listeners.foreach(handler.addProcessListener)
      exitListener.completeIfTerminated(handler)
      onStarted(descriptor)
      if (!startedFuture.complete(StartedProcess(descriptor, handler, exitListener.exitCodeFuture)))
        Disposer.dispose(cleanup)
    } catch {
      case NonFatal(error) =>
        Disposer.dispose(cleanup)
        startedFuture.completeExceptionally(error)
    }
  }

  override def processNotStarted(error: Throwable): Unit =
    startedFuture.completeExceptionally(
      Option(error).getOrElse(new AssertionError("Process did not start"))
    )
}

object RunProcessTestSupport {
  final case class StartedProcess(
    descriptor: RunContentDescriptor,
    handler: ProcessHandler,
    exitCodeFuture: CompletableFuture[Int],
  )

  private final class ExitCodeListener extends ProcessListener {
    val exitCodeFuture: CompletableFuture[Int] = new CompletableFuture[Int]()

    override def processTerminated(event: ProcessEvent): Unit =
      exitCodeFuture.complete(event.getExitCode)

    def completeIfTerminated(handler: ProcessHandler): Unit =
      if (handler.isProcessTerminated)
        Option(handler.getExitCode).foreach(code => exitCodeFuture.complete(code.intValue()))
  }

  private final class ProcessCleanup(handler: ProcessHandler, descriptor: RunContentDescriptor) extends Disposable {
    override def dispose(): Unit = {
      if (!handler.isProcessTerminated)
        handler.destroyProcess()
      descriptor.dispose()
    }
  }
}
