package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package patterns

import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder

/*
 *  CaseClauses ::= CaseClause {CaseClause}
 */
sealed abstract class CaseClauses extends ParsingRule {
  protected def parseCaseClause()(implicit builder: ScalaPsiBuilder): Boolean

  override def parse(implicit builder: ScalaPsiBuilder): Boolean = {
    val caseClausesMarker = builder.mark()
    if (!parseCaseClause()) {
      caseClausesMarker.drop()
      return false
    }
    while (parseCaseClause()) {}
    caseClausesMarker.done(ScalaElementType.CASE_CLAUSES)
    true
  }
}

object CaseClauses extends CaseClauses {
  override protected def parseCaseClause()(implicit builder: ScalaPsiBuilder): Boolean = {
    CaseClause()
  }
}

object CaseClausesWithoutBraces extends CaseClauses {
  override protected def parseCaseClause()(implicit builder: ScalaPsiBuilder): Boolean = {
    CaseClauseInBracelessCaseClauses()
  }
}
