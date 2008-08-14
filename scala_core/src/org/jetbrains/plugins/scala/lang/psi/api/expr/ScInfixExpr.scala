package org.jetbrains.plugins.scala.lang.psi.api.expr

import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement

/** 
* @author Alexander Podkhalyuzin
* Date: 06.03.2008
*/

trait ScInfixExpr extends ScExpression {
  def lOp = findChildrenByClass(classOf[ScExpression])(0)
  def operation : ScReferenceExpression = findChildrenByClass(classOf[ScExpression])(1) match {
    case re : ScReferenceExpression => re
  }
  def rOp = {
    val exprs = findChildrenByClass(classOf[ScExpression])
    if (exprs.length < 3) None else Some(exprs(2))
  }
}