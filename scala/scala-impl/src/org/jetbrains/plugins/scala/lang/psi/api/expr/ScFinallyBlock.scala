package org.jetbrains.plugins.scala.lang.psi.api.expr

import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement

trait ScFinallyBlock extends ScalaPsiElement {
  def expression: Option[ScExpression] = findChild[ScExpression]
}

object ScFinallyBlock {
  def unapply(block: ScFinallyBlock): Option[ScExpression] = block.expression
}