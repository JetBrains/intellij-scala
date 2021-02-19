package org.jetbrains.plugins.scala
package lang
package psi
package api
package expr

import org.jetbrains.plugins.scala.lang.psi.api._


import com.intellij.psi.PsiElement

/**
  * @author Alexander Podkhalyuzin
  */
trait ScWhileBase extends ScExpressionBase { this: ScWhile =>
  def condition: Option[ScExpression]

  def expression: Option[ScExpression]

  def leftParen: Option[PsiElement]

  def rightParen: Option[PsiElement]

  override protected def acceptScala(visitor: ScalaElementVisitor): Unit = {
    visitor.visitWhile(this)
  }
}

abstract class ScWhileCompanion {
  def unapply(statement: ScWhile): Option[(Option[ScExpression], Option[ScExpression])] =
    Some((statement.condition, statement.expression))
}