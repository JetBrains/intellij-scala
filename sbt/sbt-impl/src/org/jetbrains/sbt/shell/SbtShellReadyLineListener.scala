package org.jetbrains.sbt.shell

import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.extensions.LoggerExt
import org.jetbrains.sbt.shell.communication.{SbtOutputCompleteLinesProcessListener, SbtShellModeProvider, SbtShellModeProviderImpl, SbtShellOutputRecognizer}

/**
 * Monitor sbt prompt status, do something when the state changes.
 *
 * Blank lines don't change the state.
 *
 * @param whenReady callback when going into Ready state
 * @param whenWorking callback when going into Working state
 */
private class SbtShellReadyLineListener(
  debugName: String,
  whenReady: => Unit,
  whenWorking: => Unit,
  shellModeProvider: SbtShellModeProvider,
) extends SbtOutputCompleteLinesProcessListener(shellModeProvider) {

  def this(debugName: String, whenReady: => Unit, whenWorking: => Unit, project: Project) =
    this(debugName, whenReady, whenWorking, SbtShellModeProviderImpl(project))

  private var readyState: Boolean = false

  override def toString: String = s"${super.toString} ($debugName)"

  override def onLine(line: String): Unit = {
    // A blank line doesn't show that sbt started working. On Windows, the PTY can follow the prompt with empty lines.
    // Treating them as output would mark the shell as busy while sbt is waiting for a command, and no prompt would
    // follow to mark it as ready again.
    if (line.trim.isEmpty) {
      log.traceSafe("onLine: blank line ignored")
    } else {
      val sbtReady = detectSbtReadyStateFromLine(line)
      changeStateAndRunCallback(sbtReady)
    }
  }

  private def detectSbtReadyStateFromLine(line: String): Boolean = {
    val sbtReady: Boolean = SbtShellOutputRecognizer.isPromptReady(line, isNewSbtShell)
    log.traceSafe(f"onLine: (sbtReady: $sbtReady%-5s) $line")
    sbtReady
  }

  private def changeStateAndRunCallback(sbtReady: Boolean): Unit = {
    (sbtReady, readyState) match {
      case (true, false) =>
        readyState = true
        whenReady
      case (false, true) =>
        readyState = false
        whenWorking
      case _ => // ignore other cases
    }
  }
}
