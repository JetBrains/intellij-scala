package org.jetbrains.plugins.scala.lang.parser.parsing.patterns

import com.intellij.lang.PsiBuilder

/**
* @author Alexander Podkhalyuzin
* Date: 28.02.2008
*/

/*
 *  CaseClauses ::= CaseClause {CaseClause}
 */

object CaseClauses {
  def parse(builder: PsiBuilder): Boolean = {
    val caseClausesMarker = builder.mark
    if (!CaseClause.parse(builder)) {
      caseClausesMarker.drop
      return false
    }
    while (CaseClause parse builder) {}
    caseClausesMarker.done(ScalaElementTypes.CASE_CLAUSES)
    return true
  }
}