package org.jetbrains.sbt.shell

import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.extensions.LoggerExt
import org.jetbrains.sbt.shell.communication.{SbtOutputCompleteLinesProcessListener, SbtShellOutputRecognizer}

/**
 * Monitor sbt prompt status, do something when the state changes.
 *
 * @param whenReady callback when going into Ready state
 * @param whenWorking callback when going into Working state
 */
private class SbtShellReadyLineListener(
  debugName: String,
  whenReady: => Unit,
  whenWorking: => Unit,
  project: Project,
) extends SbtOutputCompleteLinesProcessListener(project) {

  private var readyState: Boolean = false

  override def toString: String = s"${super.toString} ($debugName)"

  override def onLine(line: String): Unit = {
    val sbtReady: Boolean = detectSbtReadyStateFromLine(line)
    changeStateAndRunCallback(sbtReady)
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
