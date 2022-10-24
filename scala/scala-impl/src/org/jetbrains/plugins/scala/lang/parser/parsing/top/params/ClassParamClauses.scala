package org.jetbrains.plugins.scala.lang.parser.parsing.top
package params

import org.jetbrains.plugins.scala.lang.parser.ScalaElementType
import org.jetbrains.plugins.scala.lang.parser.parsing.ParsingRule
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder

/**
 * [[ClassParamClauses]] ::= { [[ClassParamClause]] }
 * [ [nl] '(' 'implicit' ClassParams ')' ]
 */
object ClassParamClauses extends ParsingRule {

  override def parse(implicit builder: ScalaPsiBuilder): Boolean = {
    val classParamClausesMarker = builder.mark()
    while (ClassParamClause()) {}
    ImplicitClassParamClause()
    classParamClausesMarker.done(ScalaElementType.PARAM_CLAUSES)
    true
  }
}