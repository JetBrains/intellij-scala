package org.jetbrains.plugins.scala.lang.psi.api.base

import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil

/** Generic infix operation, covers infix types, patterns, and expressions.
  *
  * @author Clément Fournier
  */
trait ScInfixElement[E <: ScalaPsiElement, Ref <: ScReferenceElement] extends ScalaPsiElement {

  def left: E

  def operation: Ref

  def rightOption: Option[E]

  def isRightAssoc: Boolean = ScalaNamesUtil.clean(operation.refName).endsWith(":")

  def isLeftAssoc: Boolean = !isRightAssoc
}


object ScInfixElement {
  type AnyInfixElement = ScInfixElement[_ <: ScalaPsiElement, _ <: ScReferenceElement]

  def unapply[E <: ScalaPsiElement, Ref <: ScReferenceElement](arg: ScInfixElement[E, Ref]): Option[(E, Ref, Option[E])] =
    Some((arg.left, arg.operation, arg.rightOption))
}
