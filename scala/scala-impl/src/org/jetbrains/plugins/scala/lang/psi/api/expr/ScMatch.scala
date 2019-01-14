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
trait ScMatch extends ScExpression {
  def expr: Option[ScExpression] = findChild(classOf[ScExpression])

  def getBranches: Seq[ScExpression] = caseClauses.map { clause =>
    clause.expr.getOrElse(createExpressionFromText("{}"))
  }

  def getCaseClauses: ScCaseClauses = findChildByClassScala(classOf[ScCaseClauses])

  def caseClauses: Seq[ScCaseClause] = getCaseClauses match {
    case null => Nil
    case clauses => clauses.caseClauses
  }

  override def accept(visitor: ScalaElementVisitor): Unit = {
    visitor.visitMatchStatement(this)
  }
}

object ScMatch {

  def unapply(ms: ScMatch): Option[(ScExpression, Seq[ScCaseClause])] = ms.expr.map { expression =>
    (expression, ms.caseClauses)
  }
}