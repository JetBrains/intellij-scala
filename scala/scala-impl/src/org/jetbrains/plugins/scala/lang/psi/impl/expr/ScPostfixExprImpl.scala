package org.jetbrains.plugins.scala
package lang
package psi
package impl
package expr

import com.intellij.lang.ASTNode
import com.intellij.psi.{PsiElement, PsiElementVisitor}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.expr._

import scala.collection.Seq

/** 
* @author Alexander Podkhalyuzin
* Date: 06.03.2008
*/

class ScPostfixExprImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScPostfixExpr {
  override def toString: String = "PostfixExpression"

  def argumentExpressions: Seq[ScExpression] = Seq.empty

  def getInvokedExpr: ScExpression = operation

  def argsElement: PsiElement = operation

  override def accept(visitor: ScalaElementVisitor) {
    visitor.visitPostfixExpression(this)
  }

  override def accept(visitor: PsiElementVisitor) {
    visitor match {
      case visitor: ScalaElementVisitor => visitor.visitPostfixExpression(this)
      case _ => super.accept(visitor)
    }
  }
}