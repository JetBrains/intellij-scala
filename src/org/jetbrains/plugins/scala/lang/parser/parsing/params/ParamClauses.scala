package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package params

import com.intellij.lang.PsiBuilder

/**
* @author Alexander Podkhalyuzin
* Date: 06.03.2008
*/

/*
 * ParamClauses ::= {ParamClause} [ImplicitParamClause]
 */

object ParamClauses {
  def parse(builder: PsiBuilder): Boolean = parse(builder, false)
  def parse(builder: PsiBuilder, flag: Boolean): Boolean = {
    val paramMarker = builder.mark
    if (flag) {
      if (!ParamClause.parse(builder)) {
        builder error ErrMsg("param.clause.expected")
      }
    }
    while (ParamClause.parse(builder)) {}
    ImplicitParamClause parse builder
    paramMarker.done(ScalaElementTypes.PARAM_CLAUSES)
    return true
  }
}