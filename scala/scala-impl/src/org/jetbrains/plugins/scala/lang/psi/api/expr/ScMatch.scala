package org.jetbrains.plugins.scala.lang.psi.api.expr

import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.scala.lang.psi.api.ScalaElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.{ScCaseClause, ScCaseClauses}

trait ScMatch extends ScExpression {
  def expression: Option[ScExpression] = findChild[ScExpression]

  def expressions: Seq[ScExpression] = clauses.flatMap(_.expr)

  def caseClauses: Option[ScCaseClauses] = findChild[ScCaseClauses]

  def clauses: Seq[ScCaseClause] = caseClauses.fold(Seq.empty[ScCaseClause])(_.caseClauses)

  override protected def acceptScala(visitor: ScalaElementVisitor): Unit = {
    visitor.visitMatch(this)
  }
}

object ScMatch {

  def unapply(ms: ScMatch): Option[(ScExpression, Seq[ScCaseClause])] = ms.expression.map { expression =>
    (expression, ms.clauses)
  }
}