package org.jetbrains.plugins.scala.compiler.actions.internal.compilertrees.phasesParser

import org.jetbrains.jps.incremental.scala.{Client, MessageKind}
import org.jetbrains.plugins.scala.compiler.actions.internal.compilertrees.PhaseWithTreeText

/**
 * Parser for Scala 2 compiler output.
 * In Scala 2, phases are emitted as:
 *  1. A warning with pattern "[[syntax trees at end of <phase>]]"
 *  1. Followed by a warning starting with "package" (the tree content)
 *
 * In Scala 2 compiler tree messages are printed as warnings, without a pointer to file position.<br>
 * It contains phase and tree in different warning messages.<br>
 * Between those messages there can be some other warning messages:
 *  - compiled file name (usually only after parser phase)
 *  - saying something like "tree is unchanged since parser" (but not after each phase, for some reason)
 *  - ordinary scala code warnings (e.g. deprecation or non-exhaustive match, etc...)
 *
 */
private class Scala2PhaseParser(onPhaseDetected: PhaseWithTreeText => Unit) extends PhaseParser {

  private val Scala2TreePhaseOutputRegexp = """\[\[\s*syntax trees at end of\s+(.*?)]].*?""".r

  private var currentPhase: String = _
  private var currentTreeText: String = ""

  override def processMessage(msg: Client.ClientMsg): Unit = {
    if (msg.kind != MessageKind.Warning || msg.pointer.isDefined) {
      return
    }

    val text = msg.text
    text match {
      case Scala2TreePhaseOutputRegexp(phase) =>
        flushCurrentPhase()
        currentPhase = phase
      case _ =>
        // Tree always starts with "package", if the package is empty it's "package <empty>"
        if (currentPhase != null && text.startsWith("package")) {
          currentTreeText = text
        }
    }
  }

  override def finish(): Unit = {
    flushCurrentPhase()
  }

  private def flushCurrentPhase(): Unit = {
    if (currentPhase != null) {
      onPhaseDetected(PhaseWithTreeText(currentPhase, currentTreeText))
      currentPhase = null
      currentTreeText = ""
    }
  }
}
