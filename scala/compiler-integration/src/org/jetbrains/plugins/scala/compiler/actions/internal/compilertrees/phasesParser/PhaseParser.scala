package org.jetbrains.plugins.scala.compiler.actions.internal.compilertrees.phasesParser

import org.jetbrains.jps.incremental.scala.Client

private trait PhaseParser {
  def processMessage(msg: Client.ClientMsg): Unit
  def finish(): Unit
}
