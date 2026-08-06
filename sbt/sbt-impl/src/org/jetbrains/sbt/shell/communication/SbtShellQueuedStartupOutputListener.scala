package org.jetbrains.sbt.shell.communication

/**
 * Mirrors sbt shell startup output into a queued command before the command itself starts.
 *
 * The listener deliberately forwards only output lines and ignores ready prompts. It does not emit command lifecycle
 * events. The owner removes it when the queued command is handed over to its regular command listener.
 */
private[shell] final class SbtShellQueuedStartupOutputListener(
  shellModeProvider: SbtShellModeProvider,
  onOutputLine: String => Unit,
) extends SbtOutputCompleteLinesProcessListener(shellModeProvider) {

  override def onLine(line: String): Unit = {
    val isReady = SbtShellOutputRecognizer.isPromptReady(line, isNewSbtShell)
    if (!isReady) {
      onOutputLine(line)
    }
  }
}
