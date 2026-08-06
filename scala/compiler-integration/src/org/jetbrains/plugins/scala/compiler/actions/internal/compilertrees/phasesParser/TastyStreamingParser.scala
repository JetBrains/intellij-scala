package org.jetbrains.plugins.scala.compiler.actions.internal.compilertrees.phasesParser

import org.jetbrains.jps.incremental.scala.{Client, MessageKind}
import org.jetbrains.plugins.scala.compiler.actions.internal.compilertrees.PhaseWithTreeText

import scala.collection.mutable.ArrayBuffer

/**
 * Parses Tasty output from compiler messages.
 *
 * Tasty output comes with warning messages:
 *  - Warning: **** pickled info of class A (start marker)
 *  - Warning: Header: ... Names ... Trees ... Positions ... Attributes (intermediate output)
 *  - Warning: [possibly more intermediate output messages]
 *  - Warning: **** end of pickled info of class A (end marker)
 *
 * @note There can be multiple intermediate output messages between the start and end markers
 * @note Each source file can contain multiple outputs for every top-level class definition
 */
private class TastyStreamingParser(onPhaseDetected: PhaseWithTreeText => Unit) {

  // Example start messages:
  // **** pickled info of class/trait/object/enum MyClass
  // **** pickled info of the top-level definitions in package myPackage
  private val TastyStartPattern = """\*{2,} pickled info of (.+)""".r
  private val TastyEndPattern = """\*{2,} end of pickled info of (.+)""".r

  private var currentEntityName: String = _
  private val intermediateContent = ArrayBuffer[String]()

  /**
   * @return true if the message was captured as part of Tasty output
   */
  def processMessage(msg: Client.ClientMsg): Boolean = {
    if (msg.kind != MessageKind.Warning) {
      return false
    }

    msg.text.trim match {
      case TastyStartPattern(entityName) =>
        // If we're already processing a Tasty block, flush it (shouldn't happen but be defensive)
        if (currentEntityName != null) {
          flushCurrentTastyPhase()
        }
        currentEntityName = entityName
        intermediateContent.clear()
        true

      case TastyEndPattern(endEntityName) if currentEntityName == endEntityName =>
        flushCurrentTastyPhase()
        true

      case content if currentEntityName != null =>
        // We're inside a Tasty block, accumulate content
        intermediateContent += content
        true

      case _ =>
        false
    }
  }

  def finish(): Unit = {
    // If there's an incomplete Tasty block, flush it
    if (currentEntityName != null) {
      flushCurrentTastyPhase()
    }
  }

  private def flushCurrentTastyPhase(): Unit = {
    if (currentEntityName != null) {
      val tastyMetaPhase = PhaseWithTreeText(
        s"Tasty ($currentEntityName)",
        intermediateContent.mkString("\n"),
        PhaseWithTreeText.PhaseKind.TastyOutput
      )
      onPhaseDetected(tastyMetaPhase)

      currentEntityName = null
      intermediateContent.clear()
    }
  }
}
