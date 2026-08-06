package org.jetbrains.sbt.shell.communication

import com.intellij.openapi.project.Project
import org.jetbrains.sbt.shell.SbtProcessManager

/**
 * Provides the mode of the sbt shell whose output is being processed.
 *
 * Returns `true` for the new terminal-based sbt shell and `false` for the legacy `idea-shell` mode
 * (which is still enabled by default in 2026.1 at least)
 */
private[shell] trait SbtShellModeProvider {
  def isNewShell: Boolean
}

private[shell] final class SbtShellModeProviderImpl(project: Project) extends SbtShellModeProvider {
  /**
   * The lazy evaluation is a workaround to initialize this variable
   * only when the shell process is started and the first line from the shell is being processed.<br>
   * Potentially using this method when the shell is not started may return an incorrect result, i.e., it may return `false`
   * even though the registry is enabled and the shell will be started in the new mode.<br>
   * So be careful and use it only when it's clear that the shell is running.
   */
  override lazy val isNewShell: Boolean =
    SbtProcessManager.forProject(project).isRunWithNewShell
}
