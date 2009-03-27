package org.jetbrains.plugins.scala.lang.parser.parsing.top.params

import com.intellij.lang.PsiBuilder

/**
* @author Alexander Podkhalyuzin
* Date: 08.02.2008
*/

/*
 * ClassParamClauses ::= {ClassParamClause}
 *                       [[nl] '(' 'implicit' ClassParams ')']
 */

object ClassParamClauses {
  def parse(builder: PsiBuilder) {
    val classParamClausesMarker = builder.mark
    while (ClassParamClause parse builder) {/*parse while parsed*/}
    ImplicitClassParamClause parse builder
    classParamClausesMarker.done(ScalaElementTypes.PARAM_CLAUSES)
  }
}