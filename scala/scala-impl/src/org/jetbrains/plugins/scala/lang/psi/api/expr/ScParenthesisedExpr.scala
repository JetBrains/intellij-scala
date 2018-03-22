package org.jetbrains.plugins.scala
package lang
package psi
package api
package expr

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.api.base.ScParenthesizedElement

/**
  * @author Alexander Podkhalyuzin
  *         Date: 07.03.2008
  */
trait ScParenthesisedExpr extends ScInfixArgumentExpression with ScParenthesizedElement {
  type Kind = ScExpression

  protected def isSameTree(p: PsiElement): Boolean = p.isInstanceOf[ScExpression]

  override def innerElement: Option[ScExpression] = findChild(classOf[ScExpression])

  override def accept(visitor: ScalaElementVisitor): Unit = {
    visitor.visitExprInParent(this)
  }

  // This shouldn't be used, the algorithm for calculating isParenthesisNeeded is overridden
  override protected def getPrecedence(e: ScExpression): Int = throw new IllegalAccessException

  override def isParenthesisNeeded: Boolean = ScalaPsiUtil.needParentheses(this, innerElement.get)

  override def isParenthesisClarifying: Boolean = (getParent, innerElement.get) match {
    case (_: ScSugarCallExpr, _: ScSugarCallExpr) => true
    case _ => false
  }
}

object ScParenthesisedExpr {
  def unapply(p: ScParenthesisedExpr): Option[ScExpression] = p.innerElement
}