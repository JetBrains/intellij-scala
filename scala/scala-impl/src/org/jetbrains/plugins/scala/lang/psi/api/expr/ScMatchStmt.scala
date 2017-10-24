package org.jetbrains.plugins.scala
package lang
package psi
package api
package expr

import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.{ScCaseClause, ScCaseClauses}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createExpressionFromText

/**
 * @author Alexander Podkhalyuzin, ilyas
 */

trait ScMatchStmt extends ScExpression {
  def expr: Option[ScExpression] = findChild(classOf[ScExpression])

  def getBranches: Seq[ScExpression] = getCaseClauses match {
    case null => Seq.empty
    case c => c.caseClauses.map {
      (clause: ScCaseClause) => clause.expr match {
        case Some(expr) => expr
        case None => createExpressionFromText("{}")
      }
    }
  }

  def getCaseClauses: ScCaseClauses = findChildByClassScala(classOf[ScCaseClauses])

  def caseClauses: Seq[ScCaseClause] = {
    val cc = getCaseClauses
    if (cc == null) Nil
    else cc.caseClauses
  }

  override def accept(visitor: ScalaElementVisitor): Unit = visitor.visitMatchStatement(this)
}

object ScMatchStmt {
  def unapply(ms: ScMatchStmt): Option[(ScExpression, ScCaseClauses)] = {
    ms.expr.flatMap(Some(_, ms.getCaseClauses))
  }
}