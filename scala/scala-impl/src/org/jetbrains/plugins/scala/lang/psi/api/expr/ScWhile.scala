package org.jetbrains.plugins.scala
package lang
package psi
package api
package expr

import com.intellij.psi.PsiElement

trait ScWhile extends ScExpression {
  def condition: Option[ScExpression]

  def expression: Option[ScExpression]

  def leftParen: Option[PsiElement]

  def rightParen: Option[PsiElement]

  override protected def acceptScala(visitor: ScalaElementVisitor): Unit = {
    visitor.visitWhile(this)
  }
}

object ScWhile {
  def unapply(statement: ScWhile): Option[(Option[ScExpression], Option[ScExpression])] =
    Some((statement.condition, statement.expression))
}