package org.jetbrains.plugins.scala.lang.psi.api.expr

import statements.params.ScArguments
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement

/** 
* @author Alexander Podkhalyuzin
* Date: 06.03.2008
*/

trait ScMethodCall extends ScExpression {
  def getInvokedExpr: ScExpression = findChildByClass(classOf[ScExpression])
  def args: ScArgumentExprList = findChildByClass(classOf[ScArgumentExprList])
  def allArgumentExprLists: Array[ScArgumentExprList] = {
    getInvokedExpr match {
      case call: ScMethodCall => {
        return call.allArgumentExprLists ++ Array[ScArgumentExprList](args)
      }
      case _ => Array[ScArgumentExprList](args)
    }
  }
}