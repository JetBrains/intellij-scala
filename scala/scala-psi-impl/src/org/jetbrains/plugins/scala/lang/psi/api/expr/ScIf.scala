package org.jetbrains.plugins.scala.lang.psi.api.expr

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.api.ScalaElementVisitor

trait ScIf extends ScExpression {
  def condition: Option[ScExpression]

  def thenExpression: Option[ScExpression]

  def thenKeyword: Option[PsiElement]

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
