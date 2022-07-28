package org.jetbrains.plugins.scala
package lang
package psi
package api
package base
package patterns

trait ScCaseClauses extends ScalaPsiElement {
  def caseClause: ScCaseClause = findChild[ScCaseClause].get
  def caseClauses: Seq[ScCaseClause] = findChildren[ScCaseClause]
}

object ScCaseClauses {
  def unapplySeq(e: ScCaseClauses): Some[Seq[ScCaseClause]] = Some(e.caseClauses)
}