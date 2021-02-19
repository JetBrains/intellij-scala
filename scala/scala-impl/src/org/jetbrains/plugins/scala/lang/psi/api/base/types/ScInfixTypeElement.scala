package org.jetbrains.plugins.scala
package lang
package psi
package api
package base
package types

import org.jetbrains.plugins.scala.lang.psi.api
import org.jetbrains.plugins.scala.lang.psi.api._


/** 
* @author Alexander Podkhalyuzin
* Date: 22.02.2008
*/

/*
* Common trait for usual infix types and dotty and/or types.
*/
trait ScInfixLikeTypeElementBase extends ScTypeElementBase { this: ScInfixLikeTypeElement =>
  def left: ScTypeElement = findChild[ScTypeElement].get

  def rightOption: Option[ScTypeElement] = findChildren[ScTypeElement] match {
    case Seq(_, right) => Some(right)
    case _ => None
  }
}

trait ScInfixTypeElementBase extends ScInfixLikeTypeElement
  with ScInfixElementBase
  with ScDesugarizableToParametrizedTypeElement { this: ScInfixTypeElement =>

  override protected val typeName = "InfixType"

  type Kind = ScTypeElement
  type Reference = ScStableCodeReference

  override def operation: ScStableCodeReference = findChild[ScStableCodeReference].get

  override def desugarizedText = s"${operation.getText}[${left.getText}, ${rightOption.map(_.getText).getOrElse("Nothing")}]"
}

abstract class ScInfixTypeElementCompanion {
  /** Extracts the left and right type elements of the given infix type. */
  def unapply(arg: ScInfixTypeElement): Some[(ScTypeElement, ScStableCodeReference, Option[ScTypeElement])] =
    Some((arg.left, arg.operation, arg.rightOption))
}