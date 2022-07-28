package org.jetbrains.plugins.scala.lang.psi.api.base

import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil

/** Generic infix operation, covers infix types, patterns, and expressions.
 */
trait ScInfixElement extends ScalaPsiElement {
  //expression, type element or pattern
  type Kind <: ScalaPsiElement

  type Reference <: ScReference

  def left: Kind

  def operation: Reference

  def rightOption: Option[Kind]

  def isRightAssoc: Boolean = ScalaNamesUtil.clean(operation.refName).endsWith(":")

  def isLeftAssoc: Boolean = !isRightAssoc
}


object ScInfixElement {
  def unapply(arg: ScInfixElement): Option[(arg.Kind, arg.Reference, Option[arg.Kind])] =
    Some((arg.left, arg.operation, arg.rightOption))
}
