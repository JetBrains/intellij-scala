package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package statements

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.parsing.expressions.SelfInvocation

/**
* @author Alexander Podkhalyuzin
* Date: 13.02.2008
*/

/*
 * ConstrExpr ::= SelfInvocation
 *              | '{' SelfInvocation {semi BlockStat} '}'
 */
object ConstrExpr {

  def parse(builder: ScalaPsiBuilder): Boolean = {
    val constrExprMarker = builder.mark
    builder.getTokenType match {
      case ScalaTokenTypes.tLBRACE =>
        ConstrBlock parse builder
        constrExprMarker.drop()
        return true
      case _ =>
        SelfInvocation parse builder
        constrExprMarker.done(ScalaElementType.CONSTR_EXPR)
        return true
    }
    //this line for compiler
    true
  }
}