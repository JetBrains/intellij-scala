package org.jetbrains.plugins.scala
package lang
package psi
package api
package expr

import statements.params.ScArguments
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement

/** 
* @author Alexander Podkhalyuzin
* Date: 06.03.2008
*/

trait ScMethodCall extends ScExpression {
  def deepestInvokedExpr: ScExpression = {
    getInvokedExpr match {
      case call: ScMethodCall => {
        call.deepestInvokedExpr
      }
      case expr => expr
    }
  }

  def getInvokedExpr: ScExpression = findChildByClass(classOf[ScExpression])

  def args: ScArgumentExprList = findChildByClass(classOf[ScArgumentExprList])

  def argumentExpressions : Seq[ScExpression] = if (args != null) args.exprs else Nil

}