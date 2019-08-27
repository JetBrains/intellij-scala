package org.jetbrains.plugins.scala.lang.parser.parsing.types

import org.jetbrains.plugins.scala.lang.parser.ScalaElementType
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder

/**
 * TypeCaseClauses ::= TypeCaseClause { TypeCaseClause }
 */
object TypeCaseClauses {
  def parse(builder: ScalaPsiBuilder): Boolean = {
    val marker = builder.mark()

    if (!TypeCaseClause.parse(builder)) {
      marker.drop()
      false
    } else {
      while (TypeCaseClause.parse(builder)) {}
      marker.done(ScalaElementType.TYPE_CASE_CLAUSES)
      true
    }
  }
}
