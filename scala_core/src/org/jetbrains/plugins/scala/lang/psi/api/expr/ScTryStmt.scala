package org.jetbrains.plugins.scala.lang.psi.api.expr

/** 
* @author Alexander Podkhalyuzin
* Date: 06.03.2008
*/

trait ScTryStmt extends ScExpression {
  def catchBlock = findChildByClass(classOf[ScCatchBlock])
}