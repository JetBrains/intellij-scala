package org.jetbrains.plugins.scala
package lang
package psi
package api
package expr

import org.jetbrains.plugins.scala.lang.psi.api._


import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.{ScCaseClause, ScCaseClauses}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createExpressionFromText

/**
  * @author Alexander Podkhalyuzin, ilyas
  */
trait ScMatchBase extends ScExpressionBase { this: ScMatch =>
  def expression: Option[ScExpression] = findChild[ScExpression]

  def expressions: Seq[ScExpression] = clauses.map(_.expr.getOrElse(createExpressionFromText("{}")))

  @Nullable
  def caseClauses: Option[ScCaseClauses] = findChild[ScCaseClauses]

  def clauses: Seq[ScCaseClause] = caseClauses.fold(Seq.empty[ScCaseClause])(_.caseClauses)

  override protected def acceptScala(visitor: ScalaElementVisitor): Unit = {
    visitor.visitMatch(this)
  }
}

abstract class ScMatchCompanion {

  def unapply(ms: ScMatch): Option[(ScExpression, Seq[ScCaseClause])] = ms.expression.map { expression =>
    (expression, ms.clauses)
  }
}