package org.jetbrains.plugins.scala.lang.parser.parsing.types

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementType
import org.jetbrains.plugins.scala.lang.parser.parsing.ParsingRule
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.parsing.expressions.CaseClausesInIndentationRegion

/**
 * TypeCaseClauses ::= TypeCaseClause { TypeCaseClause }
 */
object TypeCaseClauses extends ParsingRule {
  override def parse(implicit builder: ScalaPsiBuilder): Boolean = {
    val marker = builder.mark()

    if (!TypeCaseClause()) {
      marker.drop()
      false
    } else {
      if (builder.getTokenType == ScalaTokenTypes.tSEMICOLON) {
        builder.advanceLexer()
      }
      while (!builder.isOutdentHere && TypeCaseClause()) {
        if (builder.getTokenType == ScalaTokenTypes.tSEMICOLON) {
          builder.advanceLexer()
        }
      }
      marker.done(ScalaElementType.TYPE_CASE_CLAUSES)
      true
    }
  }
}

object TypeCaseClausesInIndentationRegion extends CaseClausesInIndentationRegion {
  override def allowExprCaseClause: Boolean = false

  override def parseCaseClausesWithoutBraces()(implicit builder: ScalaPsiBuilder): Boolean =
    TypeCaseClauses()
}