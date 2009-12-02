package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package top.params

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
  def parse(builder: PsiBuilder): Boolean = {
    val classParamClausesMarker = builder.mark
    while (ClassParamClause parse builder) {}
    ImplicitClassParamClause parse builder
    classParamClausesMarker.done(ScalaElementTypes.PARAM_CLAUSES)
    true
  }
}