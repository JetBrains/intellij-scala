package org.jetbrains.plugins.scala
package lang
package psi
package api
package expr

import psi.ScalaPsiElement
import com.intellij.psi._

/** 
* @author Alexander Podkhalyuzin
* Date: 06.03.2008
*/

trait ScDoStmt extends ScExpression {
  def condition: Option[ScExpression]
  
 /**
   *  retrun loop expression of do statement
   *  @return body of do statement
   */
  def getExprBody: Option[ScExpression]

 /**
   *  return does do statement has loop expression
   *  @return has loop expression
   */
  def hasExprBody: Boolean


  override def accept(visitor: ScalaElementVisitor) = visitor.visitDoStatement(this)
}

object ScDoStmt {
  def unapply(doStmt: ScDoStmt): Option[(Option[ScExpression], Option[ScExpression])] =
    Some(doStmt.getExprBody, doStmt.condition)
}