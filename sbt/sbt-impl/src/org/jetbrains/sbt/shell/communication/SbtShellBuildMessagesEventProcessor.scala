package org.jetbrains.sbt.shell.communication

import com.intellij.build.events.impl.{FailureResultImpl, SuccessResultImpl}
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.externalSystem.model.ExternalSystemException
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.scala.build.BuildMessages.EventId
import org.jetbrains.plugins.scala.build.{BuildMessages, BuildReporter}
import org.jetbrains.sbt.SbtBundle
import org.jetbrains.sbt.process.SbtProcessOutputDiagnosticsCollector
import org.jetbrains.sbt.shell.SbtShellRunner
import org.jetbrains.sbt.shell.communication.SbtShellBuildMessagesEventProcessor.*
import org.jetbrains.sbt.shell.communication.ShellEvent.*

/**
 * Converts sbt shell command events into build messages and Build View reporter events.
 */
private[sbt] final class SbtShellBuildMessagesEventProcessor(
  project: Project,
  reporter: BuildReporter,
  dumpTaskId: EventId,
  processOutputCollector: Option[SbtProcessOutputDiagnosticsCollector],
  @Nls startMessage: String,
  @Nls finishMessage: String,
  onOutputLine: String => Unit,
  showSbtShellOnError: Boolean,
) extends SbtShellCommandEventProcessor[BuildMessages] {
  override def initialResult: BuildMessages =
    BuildMessages.empty

  override def process(messages: BuildMessages, event: ShellEvent): BuildMessages =
    event match {
      case TaskStart =>
        log.trace(s"messageAggregator TaskStart: dumpTaskId=$dumpTaskId...")

        reporter.startTask(dumpTaskId, None, startMessage)
        messages

      case TaskComplete =>
        log.trace(s"messageAggregator TaskComplete: dumpTaskId=$dumpTaskId")

        reporter.finishTask(dumpTaskId, finishMessage, new SuccessResultImpl())
        val messagesUpdated =
          if (messages.status == BuildMessages.Indeterminate) messages.status(BuildMessages.OK)
          else messages
        messagesUpdated

      case ProcessTerminated =>
        log.trace(s"messageAggregator ProcessTerminated: dumpTaskId=$dumpTaskId")

        //TODO: it seems like in practice "process terminated" is not used at all
        // we need to refactor the reporter API to not demand it
        reporter.finishTask(dumpTaskId, "process terminated", new SuccessResultImpl())
        messages
          .addError("process terminated")
          .status(BuildMessages.Canceled)

      case ErrorWaitForInput =>
        log.trace(s"messageAggregator ErrorWaitForInput: dumpTaskId=$dumpTaskId")

        val msg = SbtBundle.message("sbt.import.errors.project.reload.aborted")
        val ex = new ExternalSystemException(msg)

        val result = new FailureResultImpl(msg, ex)
        reporter.finishTask(dumpTaskId, msg, result)

        // addError records the diagnostic text, but it does not make the structure dump fail by itself.
        // Mark the result as Error so project import stops after failed reload instead of continuing as a successful dump.
        messages
          .addError(msg)
          .status(BuildMessages.Error)

      case Output(raw) =>
        // Strip ANSI codes in both old and new sbt shell modes for simplicity - it's harmless in old mode.
        val text = BuildMessages.stripAnsiCodes(raw).trim
        if (log.isTraceEnabled) {
          log.trace(s"messageAggregator Output: dumpTaskId=$dumpTaskId, text=$text")
        }

        processOutputCollector.foreach(_.append(SbtShellProcessOutputTitle, text))

        val isError = isErrorOutput(text)
        val newMessages =
          if (isError) {
            if (messages.errors.isEmpty && showSbtShellOnError) {
              SbtShellRunner.openShell(focus = false, project)
            }
            messages.addError(text.stripPrefix(ErrorPrefix))
          } else if (text `startsWith` WarnPrefix) {
            messages.addWarning(text.stripPrefix(WarnPrefix))
          } else messages

        onOutputLine(text)

        reporter.progressTask(dumpTaskId, 1, -1, SbtBundle.message("sbt.events"), text)

        if (isError) {
          reporter.logErr(text)
        } else {
          reporter.log(text)
        }

        newMessages
    }
}

private[sbt] object SbtShellBuildMessagesEventProcessor {
  private val log = Logger.getInstance(classOf[SbtShellBuildMessagesEventProcessor])

  private val WarnPrefix = "[warn]"
  private val ErrorPrefix = "[error]"
  private val SbtShellProcessOutputTitle = "SBT shell command output"

  def forSync(
    project: Project,
    reporter: BuildReporter,
    dumpTaskId: EventId,
    processOutputCollector: Option[SbtProcessOutputDiagnosticsCollector],
    @Nls startMessage: String,
    @Nls finishMessage: String,
  ): SbtShellCommandEventProcessor[BuildMessages] =
    new SbtShellBuildMessagesEventProcessor(
      project,
      reporter, dumpTaskId, processOutputCollector, startMessage, finishMessage,
      onOutputLine = _ => (),
      showSbtShellOnError = false,
    )

  def forBuild(
    project: Project,
    reporter: BuildReporter,
    dumpTaskId: EventId,
    processOutputCollector: Option[SbtProcessOutputDiagnosticsCollector],
    @Nls startMessage: String,
    @Nls finishMessage: String,
    onOutputLine: String => Unit,
  ): SbtShellCommandEventProcessor[BuildMessages] =
    new SbtShellBuildMessagesEventProcessor(
      project,
      reporter, dumpTaskId, processOutputCollector, startMessage, finishMessage,
      onOutputLine,
      showSbtShellOnError = true,
    )

  /**
   * @param sbtOutputText a line of output from the sbt shell
   * @return true if the line starts with `[error]`
   * @note technically it's not entirely correct way to detect if the output is "an error".
   *       A user can still print some text to stdout that would start with `[error]` that would not be a "sbt error".
   *       But to our latest knowledge, there is no better way to reliably get that with the way current sbt-shell communication
   *       is implemented.
   */
  def isErrorOutput(sbtOutputText: String): Boolean =
    sbtOutputText.startsWith(ErrorPrefix)
}
