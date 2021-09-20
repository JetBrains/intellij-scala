package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package params

import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder

/**
* @author Alexander Podkhalyuzin
* Date: 06.03.2008
*/

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