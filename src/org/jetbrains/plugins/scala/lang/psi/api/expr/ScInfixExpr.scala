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

trait ScInfixExpr extends ScExpression {
  def lOp = findChildrenByClassScala(classOf[ScExpression]).apply(0)
  def operation : ScReferenceExpression = findChildrenByClassScala(classOf[ScExpression]).apply(1) match {
    case re : ScReferenceExpression => re
  }
  def rOp = findChildrenByClassScala(classOf[ScExpression]).apply(2)
}