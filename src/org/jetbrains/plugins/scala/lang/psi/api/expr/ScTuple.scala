package org.jetbrains.plugins.scala
package lang
package psi
package api
package expr

import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import types.ScType

/** 
* @author Alexander Podkhalyuzin
* Date: 06.03.2008
*/

trait ScTuple extends ScExpression {
  def exprs : Seq[ScExpression] = collection.immutable.Seq(findChildrenByClassScala(classOf[ScExpression]).toSeq: _*)

  /**
   * Return possible applications without using resolve of reference to this call (to avoid SOE)
   */
  def possibleApplications: Array[Array[(String, ScType)]]

  /**
   * Return true if this expression is call
   */
  def isCall: Boolean = {
    getParent match {
      case infix: ScInfixExpr => {
        infix.isLeftAssoc match {
          case true => infix.lOp == this
          case false => infix.rOp == this
        }
      }
      case _ => false
    }
  }
}