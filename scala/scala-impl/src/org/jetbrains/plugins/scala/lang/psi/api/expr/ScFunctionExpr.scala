package org.jetbrains.plugins.scala
package lang
package psi
package api
package expr

import org.jetbrains.plugins.scala.lang.psi.api._


import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.api.statements.params._

/**
  * @author Alexander Podkhalyuzin, ilyas
  */
trait ScFunctionExprBase extends ScExpressionBase with ScControlFlowOwnerBase { this: ScFunctionExpr =>

  def parameters: Seq[ScParameter]

  def params: ScParameters

  def result: Option[ScExpression]

  def hasParentheses: Boolean

  def leftParen: Option[PsiElement]

  def rightParen: Option[PsiElement]

  override protected def acceptScala(visitor: ScalaElementVisitor): Unit = {
    visitor.visitFunctionExpression(this)
  }
}

abstract class ScFunctionExprCompanion {
  def unapply(it: ScFunctionExpr): Some[(Seq[ScParameter], Option[ScExpression])] =
    Some(it.parameters, it.result)
}