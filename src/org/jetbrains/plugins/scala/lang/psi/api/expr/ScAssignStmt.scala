package org.jetbrains.plugins.scala.lang.psi.api.expr

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement

/** 
* @author Alexander Podkhalyuzin
* Date: 06.03.2008
*/

trait ScAssignStmt extends ScExpression {
  def getLExpression: ScExpression = findChildByClass(classOf[ScExpression])

  def getRExpression: Option[ScExpression] = findLastChild(classOf[ScExpression]) match {
    case Some(expr: ScExpression) if expr != getLExpression => Some(expr)
    case _ => None
  }

  def assignName: Option[String] = {
    getLExpression match {
      case ref: ScReferenceExpression if ref.qualifier == None => Some(ref.getText)
      case _ => None
    }
  }
}