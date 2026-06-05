package org.jetbrains.sbt.shell.communication

import org.jetbrains.annotations.ApiStatus.Experimental
import org.jetbrains.sbt.shell.communication.ShellEvent.*

/**
 * Processes sbt shell events into the result value of a submitted command.
 *
 * The sbt shell reports command progress as a stream of [[ShellEvent]] values.
 * A command submitter starts with [[initialResult]] and invokes this processor for every relevant shell event, passing
 * the current result value and the new event. The returned value becomes the current result for the next event and
 * eventually completes the command future.
 *
 * @tparam Result the command result type.<br>
 *                For example:<br>
 *                `StringBuilder` when the caller collects raw sbt output.<br>
 *                `BuildMessages` when build diagnostics are collected from shell output.<br>
 *                `ProjectTaskManager.Result` when task success or failure is tracked.<br>
 *                `Unit` when shell events are consumed only for side effects.
 */
@Experimental
trait SbtShellCommandEventProcessor[Result] {
  /**
   * The result value used before any shell events are processed.
   */
  def initialResult: Result

  /**
   * Incorporate one shell event into the current command result.
   *
   * @param currentResult the result value accumulated before this event.
   * @param event         the shell event emitted while the command is running.
   * @return the result value to keep for the later events and for the completed command future.
   */
  def process(currentResult: Result, event: ShellEvent): Result

  /**
   * Run a side effect listener next to this processor while keeping this processor's result.
   */
  final def tap(listener: SbtShellCommandEventProcessor.ListenerLike): SbtShellCommandEventProcessor[Result] =
    new SbtShellCommandEventProcessor.Tap(this, listener)
}

@Experimental
object SbtShellCommandEventProcessor {
  /**
   * Ignores every shell event and keeps the current command result unchanged.
   */
  final class NoOp[Result](override val initialResult: Result) extends SbtShellCommandEventProcessor[Result] {
    override def process(currentResult: Result, event: ShellEvent): Result =
      currentResult
  }

  /**
   * Collects text output emitted while an sbt shell command is running.
   * Ignores any other event types other than [[Output]]
   */
  final class OutputCollector extends SbtShellCommandEventProcessor[StringBuilder] {
    override def initialResult: StringBuilder = new StringBuilder()

    override def process(builder: StringBuilder, event: ShellEvent): StringBuilder =
      builder.synchronized {
        event match {
          case Output(line) =>
            builder.append("\n").append(line)
          // We explicitly list all the cases not to miss any important new events types if we add new ones
          case TaskStart |
               TaskComplete |
               ProcessTerminated |
               ErrorWaitForInput =>
            ()
        }

        builder
      }
  }

  trait ListenerLike extends SbtShellCommandEventProcessor[Unit] {
    final override def initialResult: Unit = ()

    final override def process(currentResult: Unit, event: ShellEvent): Unit = {
      process(event)
    }

    def process(event: ShellEvent): Unit
  }

  /**
   * Runs a listener for every shell event and keeps only the side effects.
   */
  class ShellEventListener(listener: ShellEvent => Unit) extends ListenerLike {
    override def process(event: ShellEvent): Unit =
      listener(event)
  }

  /**
   * Runs a side-effect-only processor next to the main processor while keeping the main processor result.
   */
  final class Tap[Result](
    main: SbtShellCommandEventProcessor[Result],
    listener: SbtShellCommandEventProcessor.ListenerLike
  ) extends SbtShellCommandEventProcessor[Result] {
    override def initialResult: Result =
      main.initialResult

    override def process(currentResult: Result, event: ShellEvent): Result = {
      listener.process(event)
      main.process(currentResult, event)
    }
  }

}
