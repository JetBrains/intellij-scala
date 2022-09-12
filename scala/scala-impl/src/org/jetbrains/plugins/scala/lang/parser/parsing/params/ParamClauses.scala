package org.jetbrains.plugins.scala.lang.parser.parsing.params

import org.jetbrains.plugins.scala.lang.parser.{ErrMsg, ScalaElementType}
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder

/*
 * ParamClauses ::= {ParamClause} [ImplicitParamClause]
 */
object ParamClauses {
  def apply(expectAtLeastOneClause: Boolean = false)(implicit builder: ScalaPsiBuilder): Boolean = {
    val paramMarker = builder.mark()
    if (expectAtLeastOneClause) {
      if (!ParamClause()) {
        builder error ErrMsg("param.clause.expected")
      }
    }
    while (ParamClause()) {}
    ImplicitParamClause()
    paramMarker.done(ScalaElementType.PARAM_CLAUSES)
    true
  }
}