package org.jetbrains.plugins.scala.lang.psi.api

import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.controlFlow.Instruction

/**
 * Represents elements with control flow cached
 * @author ilyas
 */

trait ScControlFlowOwner extends ScalaPsiElement {
  def getControlFlow(cached: Boolean): Seq[Instruction]
}