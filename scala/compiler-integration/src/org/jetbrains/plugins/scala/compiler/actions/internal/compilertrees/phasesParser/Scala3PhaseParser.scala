package org.jetbrains.plugins.scala.compiler.actions.internal.compilertrees.phasesParser

import org.jetbrains.jps.incremental.scala.Client
import org.jetbrains.plugins.scala.compiler.actions.internal.compilertrees.PhaseWithTreeText

import scala.collection.mutable.ArrayBuffer

/**
 * Parser for Scala 3 compiler output.
 * Handles:
 *  1. Regular phase output: `syntax trees at end of {phase}` followed by tree content
 *  1. Tasty output: Multi-message sequences with Start/End markers
 *  1. Uncaptured messages: Collected and emitted as synthetic phases at the end
 */
private class Scala3PhaseParser(onPhaseDetected: PhaseWithTreeText => Unit) extends PhaseParser {

  //Scala 3 output example:
  //[[syntax trees at end of                    parser]] // /Users/user/../Example.scala <NEW_LINE> tree at multiliple lines
  //[[syntax trees at end of MegaPhase{dropOuterAccessors, checkNoSuperThis, flatten}]] // /Users/user/../Example.scala <NEW_LINE> tree at multiliple lines
  private val Scala3TreePhaseOutputWithTreeRegexp = """(?s)\[\[\s*syntax trees at end of\s+(.*?)]].*?\r?\n(.*)""".r

  private val tastyParser = new TastyStreamingParser(onPhaseDetected)
  private val capturedMessages = scala.collection.mutable.Set[Client.ClientMsg]()
  private val allMessages = ArrayBuffer[Client.ClientMsg]()

  override def processMessage(msg: Client.ClientMsg): Unit = {
    allMessages += msg

    // Try regular phase pattern first
    msg.text match {
      case Scala3TreePhaseOutputWithTreeRegexp(phaseText, treeText) =>
        capturedMessages += msg
        onPhaseDetected(PhaseWithTreeText(phaseText.trim, treeText.trim))
        return
      case _ =>
    }

    // Try Tasty parsing
    val wasCaptured = tastyParser.processMessage(msg)
    if (wasCaptured) {
      capturedMessages += msg
    }
  }

  override def finish(): Unit = {
    tastyParser.finish()

    val uncapturedMessages = allMessages.filterNot(capturedMessages.contains)
    // TODO: Currently it's expected that all the phases trees have a syntax similar to Scala
    //  But it's not true to all the uncaptured output (Warning/Info/Errors)
    //  Don't apply Scala syntax in the editor for the uncaptured output (see CompilerTreesDialog)
    val syntheticPhases = buildSyntheticPhasesForUncapturedOutput(uncapturedMessages.toSeq)

    syntheticPhases.foreach(onPhaseDetected)
  }

  private def buildSyntheticPhasesForUncapturedOutput(uncapturedMessages: Seq[Client.ClientMsg]): Seq[PhaseWithTreeText] = {
    val messagesByKind = uncapturedMessages
      .groupBy(_.kind)
      .toSeq
      .sortBy(_._1.toString)

    messagesByKind.flatMap { case (kind, messages) =>
      val text = messages.map(_.text).mkString("\n")
      if (text.nonEmpty)
        Some(
          PhaseWithTreeText(
            s"== ${kind.toString.toUpperCase} Output ==",
            text,
            PhaseWithTreeText.PhaseKind.UncapturedOutput
          )
        )
      else
        None
    }
  }
}
