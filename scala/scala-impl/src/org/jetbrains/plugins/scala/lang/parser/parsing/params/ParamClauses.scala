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

  def parse(builder: ScalaPsiBuilder): Boolean = parse(builder, expectAtLeastOneClause = false)

  def parse(builder: ScalaPsiBuilder, expectAtLeastOneClause: Boolean): Boolean = {
    val paramMarker = builder.mark
    if (expectAtLeastOneClause) {
      if (!ParamClause.parse(builder)) {
        builder error ErrMsg("param.clause.expected")
      }
    }
    while (ParamClause.parse(builder)) {}
    ImplicitParamClause parse builder
    paramMarker.done(ScalaElementType.PARAM_CLAUSES)
    true
  }
}