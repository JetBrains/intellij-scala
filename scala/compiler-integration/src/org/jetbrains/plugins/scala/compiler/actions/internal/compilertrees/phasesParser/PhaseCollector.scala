package org.jetbrains.plugins.scala.compiler.actions.internal.compilertrees.phasesParser

import org.jetbrains.jps.incremental.scala.Client
import org.jetbrains.plugins.scala.compiler.actions.internal.compilertrees.PhaseWithTreeText
import org.jetbrains.plugins.scala.project.ScalaLanguageLevel

/**
 * Processes compiler messages in streaming mode and detects phases as they arrive.
 * Notifies listeners when a new phase is detected.
 */
class PhaseCollector(
  languageLevel: ScalaLanguageLevel,
  onPhaseDetected: PhaseWithTreeText => Unit
) {

  private val parser = if (languageLevel.isScala3) {
    new Scala3PhaseParser(onPhaseDetected)
  } else {
    new Scala2PhaseParser(onPhaseDetected)
  }

  def processMessage(msg: Client.ClientMsg): Unit = {
    parser.processMessage(msg)
  }

  def finish(): Unit = {
    parser.finish()
  }
}
