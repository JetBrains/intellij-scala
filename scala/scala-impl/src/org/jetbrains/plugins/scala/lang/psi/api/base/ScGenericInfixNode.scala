package org.jetbrains.plugins.scala.lang.psi.api.base

import org.jetbrains.plugins.scala.lang.parser.parsing.expressions.InfixExpr
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScInfixPattern
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScInfixTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScInfixExpr

/** Generic infix operation, covers infix types, patterns, and expressions.
  *
  * @author Clément Fournier
  */
trait ScGenericInfixNode[E <: ScalaPsiElement] extends TreeMember[E] {

  def leftOperand: TreeMember[E]

  def operation: ScReferenceElement

  def rightOperand: Option[TreeMember[E]]

  def isRightAssoc: Boolean = associativity == -1

  def isLeftAssoc: Boolean = !isRightAssoc

  def associativity: Int = InfixExpr.associate(operation.refName)
}


object ScGenericInfixNode {
  def unapply(arg: ScGenericInfixNode[_]): Option[(TreeMember[_], ScReferenceElement, Option[TreeMember[_]])] = arg match {
    case p: ScInfixPattern => ScInfixPattern.unapply(p)
    case t: ScInfixTypeElement => ScInfixTypeElement.unapply(t)
    case ScInfixExpr(l, i, r) => Some((l, i, Some(r)))
    case _ => None
  }
}
