package org.jetbrains.plugins.scala.lang.psi.api.expr

import base.patterns.{ScCaseClause, ScCaseClauses}
import impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import parser.ScalaPsiCreator

/**
 * @author Alexander Podkhalyuzin, ilyas
 */

trait ScMatchStmt extends ScExpression {
  def expr = findChild(classOf[ScExpression])

  def getBranches: Seq[ScExpression] = getCaseClauses match {
    case null => Seq.empty
    case c => c.caseClauses.map {
      (clause: ScCaseClause) => clause.expr match {
        case Some(expr) => expr
        case None => ScalaPsiElementFactory.createExpressionFromText("{}", getManager)
      }
    }
  }

  def getCaseClauses: ScCaseClauses = findChildByClass(classOf[ScCaseClauses])
}