package org.jetbrains.plugins.scala.lang.psi.api.base
package patterns

import org.jetbrains.plugins.scala.lang.psi.api.{ScalaElementVisitor, ScalaPsiElement}

trait ScCaseClauses extends ScalaPsiElement {
  def caseClause: ScCaseClause = findChild[ScCaseClause].get
  def caseClauses: Seq[ScCaseClause] = findChildren[ScCaseClause]

  override protected def acceptScala(visitor: ScalaElementVisitor): Unit = {
    visitor.visitCaseClauses(this)
  }
}

object ScCaseClauses {
  def unapplySeq(e: ScCaseClauses): Some[Seq[ScCaseClause]] = Some(e.caseClauses)
}