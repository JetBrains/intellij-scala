package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package patterns

import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder

/**
* @author Alexander Podkhalyuzin
* Date: 28.02.2008
*/

/*
 *  CaseClauses ::= CaseClause {CaseClause}
 */
object CaseClauses extends CaseClauses {
  override protected val caseClause = CaseClause
}

trait CaseClauses {
  protected val caseClause: CaseClause

  def parse(builder: ScalaPsiBuilder): Boolean = {
    val caseClausesMarker = builder.mark
    if (!caseClause.parse(builder)) {
      caseClausesMarker.drop()
      return false
    }
    while (caseClause parse builder) {}
    caseClausesMarker.done(ScalaElementTypes.CASE_CLAUSES)
    return true
  }
}