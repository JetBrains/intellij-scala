package org.jetbrains.plugins.scala
package lang
package psi
package api
package base
package types

/*
* Common trait for usual infix types and dotty and/or types.
*/
trait ScInfixLikeTypeElement extends ScTypeElement {
  def left: ScTypeElement = findChild[ScTypeElement].get

  def rightOption: Option[ScTypeElement] = findChildren[ScTypeElement] match {
    case Seq(_, right) => Some(right)
    case _ => None
  }
}

trait ScInfixTypeElement extends ScInfixLikeTypeElement
  with ScInfixElement
  with ScDesugarizableToParametrizedTypeElement {

  override protected val typeName = "InfixType"

  type Kind = ScTypeElement
  type Reference = ScStableCodeReference

  override def operation: ScStableCodeReference = findChild[ScStableCodeReference].get

  override def desugarizedText = s"${operation.getText}[${left.getText}, ${rightOption.map(_.getText).getOrElse("Nothing")}]"
}

object ScInfixTypeElement {
  /** Extracts the left and right type elements of the given infix type. */
  def unapply(arg: ScInfixTypeElement): Option[(ScTypeElement, ScStableCodeReference, Option[ScTypeElement])] =
    Some((arg.left, arg.operation, arg.rightOption))
}