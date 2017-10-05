package org.jetbrains.plugins.scala
package lang
package psi
package api
package base
package patterns

/** 
* @author Alexander Podkhalyuzin
* Date: 28.02.2008
*/

trait ScCaseClauses extends ScalaPsiElement {
  def caseClause: ScCaseClause = findChildByClassScala(classOf[ScCaseClause])
  def caseClauses: Seq[ScCaseClause] = findChildrenByClassScala(classOf[ScCaseClause]).toSeq
}

object ScCaseClauses {
  def unapplySeq(e: ScCaseClauses): Some[Seq[ScCaseClause]] = Some(e.caseClauses)
}