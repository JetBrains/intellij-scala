package org.jetbrains.plugins.scala.lang.parser.parsing.types

import org.jetbrains.plugins.scala.lang.parser.ScalaElementType
import org.jetbrains.plugins.scala.lang.parser.parsing.ParsingRule
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder

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
      while (TypeCaseClause()) {}
      marker.done(ScalaElementType.TYPE_CASE_CLAUSES)
      true
    }
  }
}
