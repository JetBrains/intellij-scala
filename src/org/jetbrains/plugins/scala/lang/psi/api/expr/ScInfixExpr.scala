package org.jetbrains.plugins.scala
package lang
package psi
package api
package expr

import types.ScType

/**
* @author Alexander Podkhalyuzin
*/

trait ScInfixExpr extends ScExpression {
  def lOp: ScExpression = findChildrenByClassScala(classOf[ScExpression]).apply(0)
  def operation : ScReferenceExpression = findChildrenByClassScala(classOf[ScExpression]).apply(1) match {
    case re : ScReferenceExpression => re
  }
  def rOp: ScExpression = findChildrenByClassScala(classOf[ScExpression]).apply(2)
  def isLeftAssoc: Boolean = {
    val opText = operation.getText
    opText.endsWith(":")
  }

  /**
   * Return possible applications without using resolve of reference to this call (to avoid SOE)
   */
  def possibleApplications: Array[Array[(String, ScType)]]
}