package org.jetbrains.plugins.scala.lang.parser.parsing.top
package params

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ErrMsg
import org.jetbrains.plugins.scala.lang.parser.ScalaElementType
import org.jetbrains.plugins.scala.lang.parser.parsing.ParsingRule
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.parsing.params.TypeParamClause

/**
 * [[ClassParamClauses]] ::= { [[ClassParamClause]] }
 * [ [nl] '(' 'implicit' ClassParams ')' ]
 */
object ClassParamClauses extends ParsingRule {

  override def parse(implicit builder: ScalaPsiBuilder): Boolean = {
    val classParamClausesMarker = builder.mark()
    var hasValueParamClause = false
    var continue = true

    while (continue) {
      if (ClassParamClause()) {
        hasValueParamClause = true
      } else if (hasValueParamClause && builder.getTokenType == ScalaTokenTypes.tLSQBRACKET) {
        val typeParamClauseErrorMarker = builder.mark()
        if (TypeParamClause()) {
          typeParamClauseErrorMarker.error(ErrMsg("interleaved.type.param.clauses.in.constructors.are.not.supported"))
        } else {
          typeParamClauseErrorMarker.drop()
          continue = false
        }
      } else {
        continue = false
      }
    }

    ImplicitClassParamClause()
    classParamClausesMarker.done(ScalaElementType.PARAM_CLAUSES)
    true
  }
}
