package org.jetbrains.plugins.scala
package lang
package psi
package api
package expr

trait ScFinallyBlock extends ScalaPsiElement {
  def expression: Option[ScExpression] = findChild[ScExpression]
}

object ScFinallyBlock {
  def unapply(block: ScFinallyBlock): Option[ScExpression] = block.expression
}