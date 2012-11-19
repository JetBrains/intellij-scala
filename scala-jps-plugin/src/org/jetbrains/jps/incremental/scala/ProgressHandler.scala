package org.jetbrains.jps.incremental.scala

import xsbti.compile.CompileProgress
import org.jetbrains.jps.incremental.messages.ProgressMessage
import org.jetbrains.jps.incremental.CompileContext

/**
 * @author Pavel Fatin
 */
class ProgressHandler(context: CompileContext) extends CompileProgress {
  def startUnit(phase: String, unitPath: String) {
    context.processMessage(new ProgressMessage("Phase " + phase + " on " + unitPath))
  }

  def advance(current: Int, total: Int) = {
    context.processMessage(new ProgressMessage("", current.toFloat / total.toFloat))
    !context.getCancelStatus.isCanceled
  }
}
