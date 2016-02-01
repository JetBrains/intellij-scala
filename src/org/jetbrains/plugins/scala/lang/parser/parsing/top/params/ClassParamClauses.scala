package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package top.params

import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder

/**
* @author Alexander Podkhalyuzin
* Date: 08.02.2008
*/

/*
 * ClassParamClauses ::= {ClassParamClause}
 *                       [[nl] '(' 'implicit' ClassParams ')']
 */
object ClassParamClauses extends ClassParamClauses {
  override protected val classParamClause = ClassParamClause
  override protected val implicitClassParamClause = ImplicitClassParamClause
}

trait ClassParamClauses {
  protected val classParamClause: ClassParamClause
  protected val implicitClassParamClause: ImplicitClassParamClause

  def parse(builder: ScalaPsiBuilder): Boolean = {
    val classParamClausesMarker = builder.mark
    while (classParamClause parse builder) {}
    implicitClassParamClause parse builder
    classParamClausesMarker.done(ScalaElementTypes.PARAM_CLAUSES)
    true
  }
}