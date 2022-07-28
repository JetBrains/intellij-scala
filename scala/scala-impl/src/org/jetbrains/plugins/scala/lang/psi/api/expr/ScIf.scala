package org.jetbrains.plugins.scala
package lang
package psi
package api
package expr

import com.intellij.psi.PsiElement

trait ScIf extends ScExpression {
  def condition: Option[ScExpression]

  def thenExpression: Option[ScExpression]

  def elseKeyword: Option[PsiElement]

  def elseExpression: Option[ScExpression]

  def leftParen: Option[PsiElement]

  def rightParen: Option[PsiElement]

  override protected def acceptScala(visitor: ScalaElementVisitor): Unit = {
    visitor.visitIf(this)
  }
}

object ScIf {
  def unapply(ifStmt: ScIf): Some[(Option[ScExpression], Option[ScExpression], Option[ScExpression])] = Some(ifStmt.condition, ifStmt.thenExpression, ifStmt.elseExpression)
}