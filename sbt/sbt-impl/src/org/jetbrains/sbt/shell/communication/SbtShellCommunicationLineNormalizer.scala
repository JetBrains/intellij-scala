package org.jetbrains.sbt.shell.communication

/**
 * Normalizes physical process lines before they are passed to sbt shell communication listeners.
 *
 * Sbt shell communication is driven by a text protocol over process output. Most physical process lines can be passed
 * through unchanged, but debugger/JDWP control output can be glued to sbt prompts because stdout and stderr are read
 * asynchronously and process writes are buffered independently.
 *
 * This normalizer handles only the known JDWP listening banner workaround.
 * It intentionally preserves any suspicious text as regular command output unless the banner is exact
 * and either standalone or glued directly after a recognized prompt.
 */
private[shell] object SbtShellCommunicationLineNormalizer {

  /**
   * Checks whether an incomplete physical line is already meaningful enough for shell communication.
   *
   * Ready/error prompts can arrive without a trailing line separator, so they must be flushed immediately.
   * The exception is a prompt followed by an incomplete JDWP listening banner prefix:
   * in that case the line must stay buffered until the whole banner is available and can be filtered.
   *
   * Examples:
   * {{{
   * isIncompleteLineReadyForCommunication("[IJ]>", isNewSbtShell = false)
   * // returns true
   *
   * isIncompleteLineReadyForCommunication("sbt:mock>", isNewSbtShell = true)
   * // returns true
   *
   * isIncompleteLineReadyForCommunication("[IJ]>Listening", isNewSbtShell = false)
   * // returns false
   *
   * isIncompleteLineReadyForCommunication(
   *   "Project loading failed: (r)etry, (q)uit, (l)ast, or (i)gnore?",
   *   isNewSbtShell = true,
   * )
   * // returns true
   * }}}
   */
  def isIncompleteLineReadyForCommunication(line: String, isNewSbtShell: Boolean): Boolean =
    // If the current tail looks like "<prompt>Listening for..." but the JDWP banner is incomplete,
    // keep buffering. Emitting only the prompt now would lose the ability to drop the banner suffix later.
    !isPromptGluedToIncompleteJdwpListeningPrefix(line, isNewSbtShell) &&
      communicationLines(line, isNewSbtShell).exists { communicationLine =>
        SbtShellOutputRecognizer.isPromptReady(communicationLine, isNewSbtShell) ||
          SbtShellOutputRecognizer.isProjectLoadingPromptError(communicationLine)
      }

  /**
   * Converts one physical process line into zero, one, or several shell communication lines.
   *
   * The only currently supported line-level rewrite is the JDWP listening banner. JDWP writes this banner directly to
   * the debuggee process streams, and after debugger detach it can be glued to the sbt prompt by stream buffering.
   *
   * Examples:
   * {{{
   * communicationLines("Listening for transport dt_socket at address: 12345", isNewSbtShell = false)
   * // returns Seq.empty
   *
   * communicationLines("[IJ]>Listening for transport dt_socket at address: 12345", isNewSbtShell = false)
   * // returns Seq("[IJ]>")
   *
   * communicationLines("sbt:mock>Listening for transport dt_shmem at address: javadebug", isNewSbtShell = true)
   * // returns Seq("sbt:mock>")
   *
   * communicationLines("regular output: Listening for transport dt_socket at address: 12345", isNewSbtShell = false)
   * // returns the original line unchanged
   * }}}
   *
   * The banner is dropped only when it is standalone or glued after a recognized prompt. Any other occurrence is
   * preserved as regular command output.
   */
  def communicationLines(line: String, isNewSbtShell: Boolean): Seq[String] = {
    val jdwpMessageIndex = SbtShellOutputRecognizer.indexOfJdwpListeningBanner(line)
    if (jdwpMessageIndex < 0)
      Seq(line)
    else
      communicationLinesWithJdwpBanner(line, jdwpMessageIndex, isNewSbtShell)
  }

  private def communicationLinesWithJdwpBanner(
    line: String,
    jdwpMessageIndex: Int,
    isNewSbtShell: Boolean,
  ): Seq[String] = {
    val beforeJdwpMessage = line.substring(0, jdwpMessageIndex)
    val afterJdwpMessage = line.substring(jdwpMessageIndex)

    // Ignore the suffix only if it is the complete JDWP banner.
    // A partial banner is kept buffered by SbtOutputCompleteLinesProcessListener via isIncompleteLineReadyForCommunication.
    val isCompleteJdwpListeningBanner =
      SbtShellOutputRecognizer.isJdwpListeningBanner(afterJdwpMessage)
    // If text before the banner is not a prompt, the line may be normal command output and must be preserved.
    val isStandaloneOrPromptGluedBanner =
      beforeJdwpMessage.isEmpty || SbtShellOutputRecognizer.isPromptReady(beforeJdwpMessage, isNewSbtShell)
    val shouldDropJdwpListeningBanner = isCompleteJdwpListeningBanner && isStandaloneOrPromptGluedBanner

    if (shouldDropJdwpListeningBanner)
      if (beforeJdwpMessage.nonEmpty)
        Seq(beforeJdwpMessage)
      else
        Seq.empty
    else
      Seq(line)
  }

  /**
   * Checks whether the current incomplete tail is a prompt followed by the start of a JDWP listening banner.
   *
   * This prevents a subtle race:
   * {{{
   * chunk 1: "[IJ]>Listening"
   * chunk 2: " for transport dt_socket at address: 12345"
   * }}}
   *
   * If chunk 1 were flushed as a prompt immediately, chunk 2 would later look like regular output and the JDWP banner
   * would leak into shell communication. Holding chunk 1 until the banner is complete lets [[communicationLines]] split
   * the prompt and drop the banner consistently.
   */
  private def isPromptGluedToIncompleteJdwpListeningPrefix(line: String, isNewSbtShell: Boolean): Boolean = {
    val promptWithSuffix = promptWithPossibleJdwpSuffix(line, isNewSbtShell)
    promptWithSuffix.exists { case (_, suffix) =>
      suffix.nonEmpty && SbtShellOutputRecognizer.isJdwpListeningBannerPrefix(suffix)
    }
  }

  /**
   * Finds a prompt candidate at the beginning of the line and returns the text after that prompt as a possible suffix.
   *
   * The search walks prompt-ending `>` characters from right to left because both old and new prompts end with `>`, and
   * the project name inside a new sbt prompt may contain other text before the final prompt marker.
   *
   * Examples:
   * {{{
   * promptWithPossibleJdwpSuffix("[IJ]>Listening", isNewSbtShell = false)
   * // returns Some("[IJ]>" -> "Listening")
   *
   * promptWithPossibleJdwpSuffix("sbt:mock>Listening", isNewSbtShell = true)
   * // returns Some("sbt:mock>" -> "Listening")
   *
   * promptWithPossibleJdwpSuffix("regular output > Listening", isNewSbtShell = false)
   * // returns None
   * }}}
   */
  private def promptWithPossibleJdwpSuffix(line: String, isNewSbtShell: Boolean): Option[(String, String)] = {
    val promptEndIndices: Iterator[Int] = line
      .indices
      .reverseIterator
      .filter(line.charAt(_) == '>')

    val promptCandidates: Iterator[(String, String)] =
      promptEndIndices.map { promptEnd =>
        val prompt = line.substring(0, promptEnd + 1)
        val suffix = line.substring(promptEnd + 1)
        prompt -> suffix
      }

    promptCandidates.find { case (prompt, _) =>
      SbtShellOutputRecognizer.isPromptReady(prompt, isNewSbtShell)
    }
  }
}
