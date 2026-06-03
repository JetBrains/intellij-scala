package org.jetbrains.plugins.scala.lang.parser.parsing.params

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.{ErrMsg, ScalaElementType}
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder

/*
 * ParamClauses ::= {ParamClause} [ImplicitParamClause]
 */
object ParamClauses {
  def apply(expectAtLeastOneClause: Boolean = false, allowInterleavingTypeParamClauses: Boolean = false)(implicit builder: ScalaPsiBuilder): Boolean = {
    val paramMarker = builder.mark()
    var hasValueClauseAfterLastType = false

    if (expectAtLeastOneClause) {
      val hasClause = ParamClause()
      if (!hasClause) {
        builder error ErrMsg("param.clause.expected")
      } else {
        hasValueClauseAfterLastType = true
      }
    }

    var continue = true
    while (continue) {
      if (ParamClause()) {
        hasValueClauseAfterLastType = true
      } else if (allowInterleavingTypeParamClauses && hasValueClauseAfterLastType && FunTypeParamClause()) {
        hasValueClauseAfterLastType = false
      } else if (!allowInterleavingTypeParamClauses && hasValueClauseAfterLastType && builder.getTokenType == ScalaTokenTypes.tLSQBRACKET) {
        builder error ErrMsg("param.clause.expected")
        if (FunTypeParamClause()) {
          hasValueClauseAfterLastType = false
        } else {
          continue = false
        }
      } else if (allowInterleavingTypeParamClauses && !hasValueClauseAfterLastType && builder.getTokenType == ScalaTokenTypes.tLSQBRACKET) {
        builder error ErrMsg("param.clause.expected")
        if (FunTypeParamClause()) {
          hasValueClauseAfterLastType = false
        } else {
          continue = false
        }
      } else {
        continue = false
      }
    }

    ImplicitParamClause()
    paramMarker.done(ScalaElementType.PARAM_CLAUSES)
    true
  }
}
