package org.jetbrains.sbt.shell.communication

import java.util.regex.Pattern
import scala.util.matching.Regex

private[shell] object SbtShellOutputRecognizer {

  /**
   * The prompt marker is inserted by the `sbt-idea-shell plugin`.
   * Should be the same as in `org.jetbrains.sbt.constants.IDEA_PROMPT_MARKER`
   */
  private val IdeaShellPromptMarker = "[IJ]"

  private val DefaultSbtPromptPrefix = "sbt:"

  private val JdwpListeningBannerStart: String =
    "Listening for transport"

  // Matches the complete JDWP listening banner, for example, "Listening for transport dt_socket at address: 12345".
  private val JdwpListeningBannerPattern =
    ("^\\s*" + Pattern.quote(JdwpListeningBannerStart) + "\\s+\\S+\\s+at address:\\s+\\S+\\s*$").r

  // Matches CSI escape sequences, for example, ESC[31m for color and ESC[?2004h for bracketed paste.
  private val AnsiCsiEscapeSequencePattern: Regex =
    "\u001B\\[[;?0-9]*[ -/]*[@-~]".r

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
  def isPromptReady(line: String, withNewShell: Boolean): Boolean =
    if (withNewShell) {
      // When using the new shell (with the built-in shell command), jline3 is utilized under the hood since sbt 1.4.
      // Before displaying any prompt, jline3 prints the BRACKETED_PASTE_ON escape sequence to the terminal to enable bracketed paste mode.
      // If a line contains this escape sequence, it indicates that the line contains a prompt.
      // This readiness workaround exists for SCL-12187. The original fix has since been enhanced with the fallback below.
      // As a fallback, we check if the line starts with the default shell prompt ("sbt:project_name").
      // This heuristic may fail for users with custom prompts but should work for most standard configurations.
      val bracketedPasteModeEnabled = "\u001B[?2004h"
      val isBracket = line.contains(bracketedPasteModeEnabled)
      isBracket || {
        val lineWithNoAnsi = AnsiCsiEscapeSequencePattern.replaceAllIn(line, "")
        lineWithNoAnsi.trim.startsWith(DefaultSbtPromptPrefix)
      }
    } else {
      line.trim.startsWith(IdeaShellPromptMarker)
    }

  def isProjectLoadingPromptError(line: String): Boolean =
    line.trim.contains("Project loading failed: (r)etry, (q)uit, (l)ast, or (i)gnore?")

  /**
   * JDWP writes this banner directly to the debuggee process streams.<br>
   * Around debugger detach it can be interleaved with the sbt prompt,
   * so shell communication must treat the exact banner as debugger control noise, not sbt command output.
   *
   * We need to keep the shell ready if JDWP prints this line while the prompt is already visible.
   */
  def isJdwpListeningBanner(line: String): Boolean =
    JdwpListeningBannerPattern.pattern.matcher(line).matches()

  def indexOfJdwpListeningBanner(line: String): Int =
    line.indexOf(JdwpListeningBannerStart)

  def isJdwpListeningBannerPrefix(line: String): Boolean =
    (JdwpListeningBannerStart.startsWith(line) || line.startsWith(JdwpListeningBannerStart)) &&
      !isJdwpListeningBanner(line)
}
