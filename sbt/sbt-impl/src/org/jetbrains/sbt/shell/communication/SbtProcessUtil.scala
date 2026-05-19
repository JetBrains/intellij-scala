package org.jetbrains.sbt.shell.communication

import org.jetbrains.plugins.scala.build.BuildMessages

private[shell] object SbtProcessUtil {

  /**
   * The prompt marker is inserted by the `sbt-idea-shell plugin`.
   * Should be the same as in `org.jetbrains.sbt.constants.IDEA_PROMPT_MARKER`
   */
  private val IDEA_PROMPT_MARKER = "[IJ]"

  private val DEFAULT_SHELL_PROMPT = "sbt:"

  /**
   * Detects that an sbt shell prompt is ready to accept the next command.
   *
   * In the legacy shell, the prompt is recognized by the IntelliJ prompt marker injected by the `sbt-idea-shell` plugin.
   *
   * In the new shell, sbt writes the regular prompt itself, so readiness is detected by the jline bracketed-paste escape sequence
   * or, as a fallback, by the default `sbt:` prompt prefix after stripping ANSI codes.
   *
   * @param line         a complete output line, or the current unfinished output tail
   * @param withNewShell whether the shell uses sbt's built-in shell command
   * @return `true` when the line indicates that the shell can receive a command
   */
  def promptReady(line: String, withNewShell: Boolean): Boolean =
    if (withNewShell) {
      // When using the new shell (with the built-in shell command), jline3 is utilized under the hood since sbt 1.4.
      // Before displaying any prompt, jline3 prints the BRACKETED_PASTE_ON escape sequence to the terminal to enable bracketed paste mode.
      // If a line contains this escape sequence, it indicates that the line contains a prompt.
      // As a fallback, we check if the line starts with the default shell prompt ("sbt:project_name").
      // This heuristic may fail for users with custom prompts but should work for most standard configurations.
      val bracketedPasteModeEnabled = "\u001B[?2004h"
      val isBracket = line.contains(bracketedPasteModeEnabled)
      isBracket || {
        val lineWithNoAnsi = BuildMessages.stripAnsiCodes(line)
        lineWithNoAnsi.trim.startsWith(DEFAULT_SHELL_PROMPT)
      }
    } else {
      line.trim.startsWith(IDEA_PROMPT_MARKER)
    }

  def promptError(line: String): Boolean =
    line.trim.contains("Project loading failed: (r)etry, (q)uit, (l)ast, or (i)gnore?")

  // "sucky" workaround for jdwp printing this line on the console when deactivating debugger
  def debuggerMessage(line: String): Boolean =
    line.contains("Listening for transport")
}
