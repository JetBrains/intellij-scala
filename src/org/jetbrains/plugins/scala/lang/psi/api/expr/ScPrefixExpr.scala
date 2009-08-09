package org.jetbrains.plugins.scala
package lang
package psi
package api
package expr

import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement

/** 
* @author Alexander Podkhalyuzin
* Date: 06.03.2008
*/

trait ScPrefixExpr extends ScExpression {
  def operand = findChildrenByClass(classOf[ScExpression])(1)
  def operation : ScReferenceExpression = findChildrenByClass(classOf[ScExpression])(0) match {
    case re : ScReferenceExpression => re
  }
}