package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package patterns

import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder

/**
  * @author Alexander Podkhalyuzin
  *         Date: 28.02.2008
  */

/*
 *  CaseClauses ::= CaseClause {CaseClause}
 */
object CaseClauses {

  def parse(builder: ScalaPsiBuilder): Boolean = {
    val caseClausesMarker = builder.mark
    if (!CaseClause.parse(builder)) {
      caseClausesMarker.drop()
      return false
    }
    while (CaseClause.parse(builder)) {}
    caseClausesMarker.done(ScalaElementType.CASE_CLAUSES)
    true
  }
}