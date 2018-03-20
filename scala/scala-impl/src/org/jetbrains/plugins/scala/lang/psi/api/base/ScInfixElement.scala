package org.jetbrains.plugins.scala.lang.psi.api.base

import org.jetbrains.plugins.scala.lang.parser.parsing.expressions.InfixExpr
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement

/** Generic infix operation, covers infix types, patterns, and expressions.
  *
  * @author Clément Fournier
  */
trait ScInfixElement[E <: ScalaPsiElement, Ref <: ScReferenceElement] extends ScalaPsiElement {

  def leftOperand: E

  def operation: Ref

  def rightOperand: Option[E]

  def isRightAssoc: Boolean = associativity == -1

  def isLeftAssoc: Boolean = !isRightAssoc

  def associativity: Int = InfixExpr.associate(operation.refName)
}


object ScInfixElement {
  type AnyInfixElement = ScInfixElement[_ <: ScalaPsiElement, _ <: ScReferenceElement]

  def unapply[E <: ScalaPsiElement, Ref <: ScReferenceElement](arg: ScInfixElement[E, Ref]): Option[(E, Ref, Option[E])] =
    Some((arg.leftOperand, arg.operation, arg.rightOperand))
}
