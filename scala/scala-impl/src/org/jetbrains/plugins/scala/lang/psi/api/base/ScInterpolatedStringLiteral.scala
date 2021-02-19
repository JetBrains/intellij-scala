package org.jetbrains.plugins.scala
package lang
package psi
package api
package base

import org.jetbrains.plugins.scala.lang.psi.api._
import org.jetbrains.plugins.scala.lang.psi.api.base.literals.{ScStringLiteral, ScStringLiteralBase}
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScMethodCall, ScReferenceExpression}

/**
  * User: Dmitry Naydanov
  * Date: 3/17/12
  */
trait ScInterpolatedStringLiteralBase extends ScStringLiteralBase with ScInterpolatedBase { this: ScInterpolatedStringLiteral =>

  def kind: base.ScInterpolatedStringLiteral.Kind

  def reference: Option[ScReferenceExpression]

  def referenceName: String

  def desugaredExpression: Option[(ScReferenceExpression, ScMethodCall)]
}

abstract class ScInterpolatedStringLiteralCompanion {

  def unapply(literal: ScInterpolatedStringLiteral): Option[ScReferenceExpression] =
    literal.reference

  sealed trait Kind

  case object Standard extends Kind

  case object Format extends Kind

  case object Pattern extends Kind

  case object Raw extends Kind
}