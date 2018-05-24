package org.jetbrains.plugins.scala.lang.psi.api.base

import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement

/** Generic type for parenthesised nodes.
  *
  * @author Clément Fournier
  */
trait ScParenthesizedElement extends ScalaPsiElement {
  /** Expression, type element or pattern */
  type Kind <: ScalaPsiElement

  /** Returns the expression, type or pattern that is contained
    * within those parentheses.
    */
  def innerElement: Option[Kind]

  /** Check if parent of this element is of same kind*/
  def sameTreeParent: Option[Kind]
}


object ScParenthesizedElement {
  def unapply(p: ScParenthesizedElement): Option[p.Kind] = p.innerElement
}