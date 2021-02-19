package org.jetbrains.plugins.scala.lang.psi.api

import org.jetbrains.plugins.scala.lang.psi.api._


import org.jetbrains.plugins.scala.caches.ModTracker
import org.jetbrains.plugins.scala.lang.psi.controlFlow.Instruction
import org.jetbrains.plugins.scala.lang.psi.controlFlow.impl.ScalaControlFlowBuilder
import org.jetbrains.plugins.scala.macroAnnotations.Cached

/**
 * Represents elements with control flow cached
 * @author ilyas
 */

trait ScControlFlowOwnerBase extends ScalaPsiElementBase { this: ScControlFlowOwner =>

  @Cached(ModTracker.physicalPsiChange(getProject), this)
  def getControlFlow: Seq[Instruction] = {
    val builder = new ScalaControlFlowBuilder(null, null)
    controlFlowScope match {
      case Some(elem) => builder.buildControlflow(elem)
      case None => Seq.empty
    }
  }

  def controlFlowScope: Option[ScalaPsiElement]
}