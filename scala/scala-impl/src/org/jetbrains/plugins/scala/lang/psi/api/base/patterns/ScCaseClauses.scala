package org.jetbrains.plugins.scala
package lang
package psi
package api
package base
package patterns

import org.jetbrains.plugins.scala.lang.psi.api._


/** 
* @author Alexander Podkhalyuzin
* Date: 28.02.2008
*/

trait ScCaseClausesBase extends ScalaPsiElementBase { this: ScCaseClauses =>
  def caseClause: ScCaseClause = findChild[ScCaseClause].get
  def caseClauses: Seq[ScCaseClause] = findChildren[ScCaseClause]
}

abstract class ScCaseClausesCompanion {
  def unapplySeq(e: ScCaseClauses): Some[Seq[ScCaseClause]] = Some(e.caseClauses)
}