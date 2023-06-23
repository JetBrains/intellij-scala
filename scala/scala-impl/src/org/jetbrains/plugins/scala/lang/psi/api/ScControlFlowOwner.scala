package org.jetbrains.plugins.scala.lang.psi.api

import org.jetbrains.plugins.scala.caches.{ModTracker, cached}
import org.jetbrains.plugins.scala.lang.psi.controlFlow.Instruction
import org.jetbrains.plugins.scala.lang.psi.controlFlow.impl.ScalaControlFlowBuilder

/**
 * Represents elements with control flow cached
 */

trait ScControlFlowOwner extends ScalaPsiElement {

  def getControlFlow: Seq[Instruction] = _getControlFlow()

  private val _getControlFlow = cached("getControlFlow", ModTracker.physicalPsiChange(getProject), () => {
    val builder = new ScalaControlFlowBuilder(null, null)
    controlFlowScope match {
      case Some(elem) => builder.buildControlflow(elem)
      case None => Seq.empty
    }
  })

  def controlFlowScope: Option[ScalaPsiElement]
}
