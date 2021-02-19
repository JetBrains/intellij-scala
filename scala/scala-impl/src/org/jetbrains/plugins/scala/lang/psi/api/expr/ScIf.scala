package org.jetbrains.plugins.scala
package lang
package psi
package api
package expr

import org.jetbrains.plugins.scala.lang.psi.api._


import com.intellij.psi.PsiElement

/**
  * @author Alexander Podkhalyuzin
  *         Date: 06.03.2008
  */
trait ScIfBase extends ScExpressionBase { this: ScIf =>
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

abstract class ScIfCompanion {
  def unapply(ifStmt: ScIf): Some[(Option[ScExpression], Option[ScExpression], Option[ScExpression])] = Some(ifStmt.condition, ifStmt.thenExpression, ifStmt.elseExpression)
}