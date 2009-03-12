package org.jetbrains.plugins.scala.lang.psi.api.expr

/** 
* @author Alexander Podkhalyuzin
* Date: 06.03.2008
*/

trait ScTryStmt extends ScExpression {
  def tryBlock = findChildByClass(classOf[ScTryBlock])
  def catchBlock = findChild(classOf[ScCatchBlock])
  def finallyBlock = findChild(classOf[ScFinallyBlock])
}