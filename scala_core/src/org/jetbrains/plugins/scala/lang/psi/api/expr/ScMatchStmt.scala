package org.jetbrains.plugins.scala.lang.psi.api.expr

import base.patterns.{ScCaseClause, ScCaseClauses}
import impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import parser.ScalaPsiCreator

/** 
* @author Alexander Podkhalyuzin
* Date: 06.03.2008
*/

trait ScMatchStmt extends ScExpression {
  def expr = findChild(classOf[ScExpression])
  def getBranches: Seq[ScExpression] = getCaseClauses.caseClauses.map {
    (clause: ScCaseClause) => clause.expr match {
      case Some(expr) => expr
      case None => ScalaPsiElementFactory.createExpressionFromText("{}", getManager)
    }
  }
  def getCaseClauses: ScCaseClauses = findChildByClass(classOf[ScCaseClauses])
}