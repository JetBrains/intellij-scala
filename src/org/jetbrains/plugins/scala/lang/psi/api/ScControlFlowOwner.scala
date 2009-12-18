package org.jetbrains.plugins.scala.lang.psi.api

import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.controlFlow.Instruction
/**
 * @author ilyas
 */

trait ScControlFlowOwner extends ScalaPsiElement {
  def getControlFlow: Seq[Instruction]
}