package org.jetbrains.plugins.scala
package lang
package psi
package api
package expr

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.api.statements.params._

trait ScFunctionExpr extends ScExpression with ScControlFlowOwner {

  def parameters: Seq[ScParameter]

  def params: ScParameters

  def result: Option[ScExpression]

  def hasParentheses: Boolean

  def leftParen: Option[PsiElement]

  def rightParen: Option[PsiElement]

  def isContext: Boolean

  override protected def acceptScala(visitor: ScalaElementVisitor): Unit = {
    visitor.visitFunctionExpression(this)
  }
}

object ScFunctionExpr {
  def unapply(it: ScFunctionExpr): Some[(Seq[ScParameter], Option[ScExpression])] =
    Some(it.parameters, it.result)
}