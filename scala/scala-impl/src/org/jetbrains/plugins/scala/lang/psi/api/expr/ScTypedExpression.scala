package org.jetbrains.plugins.scala
package lang
package psi
package api
package expr

import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.base.types.{ScSequenceArg, ScTypeElement}

trait ScTypedExpression extends ScExpression {
  def expr: ScExpression = findChild[ScExpression].get

  def typeElement: Option[ScTypeElement] = findChild[ScTypeElement]

  def isSequenceArg: Boolean = getLastChild.is[ScSequenceArg]

  override protected def acceptScala(visitor: ScalaElementVisitor): Unit = {
    visitor.visitTypedExpr(this)
  }
}

object ScTypedExpression {
  def unapply(typed: ScTypedExpression): Option[(ScExpression, ScTypeElement)] =
    typed.typeElement.map(typed.expr -> _)

  object sequenceArg {
    def unapply(typed: ScTypedExpression): Option[ScSequenceArg] =
      typed.findLastChild[ScSequenceArg]
  }
}