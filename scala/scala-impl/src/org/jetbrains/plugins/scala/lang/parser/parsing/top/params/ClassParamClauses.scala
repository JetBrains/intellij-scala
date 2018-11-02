package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package top.params

import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder

/**
  * @author Alexander Podkhalyuzin
  *         Date: 08.02.2008
  */

/*
 * ClassParamClauses ::= {ClassParamClause}
 *                       [[nl] '(' 'implicit' ClassParams ')']
 */
object ClassParamClauses {

  def parse(builder: ScalaPsiBuilder): Boolean = {
    val classParamClausesMarker = builder.mark
    while (ClassParamClause.parse(builder)) {}
    ImplicitClassParamClause.parse(builder)
    classParamClausesMarker.done(ScalaElementType.PARAM_CLAUSES)
    true
  }
}