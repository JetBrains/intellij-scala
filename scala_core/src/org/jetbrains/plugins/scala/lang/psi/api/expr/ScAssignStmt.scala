package org.jetbrains.plugins.scala.lang.psi.api.expr

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement

/** 
* @author Alexander Podkhalyuzin
* Date: 06.03.2008
*/

trait ScAssignStmt extends ScExpression {
  def getLExpression: ScExpression = findChildByClass(classOf[ScExpression])
  def getRExpression: Option[ScExpression] = getLastChild match {
    case expr: ScExpression => Some(expr)
    case _ => None
  }
}