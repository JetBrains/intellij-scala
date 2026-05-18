package org.jetbrains.sbt.shell.communication

import org.jetbrains.plugins.scala.build.BuildMessages

private[shell] object SbtProcessUtil {

  /**
   * The prompt marker is inserted by the `sbt-idea-shell plugin`.
   * Should be the same as in `org.jetbrains.sbt.constants.IDEA_PROMPT_MARKER`
   */
  private val IDEA_PROMPT_MARKER = "[IJ]"

  private val DEFAULT_SHELL_PROMPT = "sbt:"

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

  implicit class StringExt(private val str: String) extends AnyVal {
    def trimRight: String = str.replaceAll("\\s+$", "")
  }
}