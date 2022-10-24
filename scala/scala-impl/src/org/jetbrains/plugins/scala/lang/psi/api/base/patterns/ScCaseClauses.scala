package org.jetbrains.plugins.scala.lang.psi.api.base
package patterns

import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement

trait ScCaseClauses extends ScalaPsiElement {
  def caseClause: ScCaseClause = findChild[ScCaseClause].get
  def caseClauses: Seq[ScCaseClause] = findChildren[ScCaseClause]
}

object ScCaseClauses {
  def unapplySeq(e: ScCaseClauses): Some[Seq[ScCaseClause]] = Some(e.caseClauses)
}