package org.jetbrains.plugins.scala.lang.psi.api.expr

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.api.ScalaElementVisitor

trait ScWhile extends ScExpression {
  def condition: Option[ScExpression]

  def expression: Option[ScExpression]

  def leftParen: Option[PsiElement]

  def rightParen: Option[PsiElement]

  def doKeyword: Option[PsiElement]

  override protected def acceptScala(visitor: ScalaElementVisitor): Unit = {
    visitor.visitWhile(this)
  }
}

object ScWhile {
  def unapply(statement: ScWhile): Option[(Option[ScExpression], Option[ScExpression])] =
    Some((statement.condition, statement.expression))
}
