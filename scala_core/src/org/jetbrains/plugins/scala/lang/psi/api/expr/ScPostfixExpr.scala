package org.jetbrains.plugins.scala.lang.psi.api.expr

import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement

/** 
* @author Alexander Podkhalyuzin
* Date: 06.03.2008
*/

trait ScPostfixExpr extends ScExpression {
  def operand = findChildrenByClass(classOf[ScExpression])(0)
  def operation : ScReferenceExpression = findChildrenByClass(classOf[ScExpression])(1) match {
    case re : ScReferenceExpression => re
  }
}