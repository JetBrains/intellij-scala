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
object ConstrExpr extends ConstrExpr {
  override protected val constrBlock = ConstrBlock
  override protected val selfInvocation = SelfInvocation
}

trait ConstrExpr {
  protected val constrBlock: ConstrBlock
  protected val selfInvocation: SelfInvocation

  def parse(builder: ScalaPsiBuilder): Boolean = {
    val constrExprMarker = builder.mark
    builder.getTokenType match {
      case ScalaTokenTypes.tLBRACE =>
        constrBlock parse builder
        constrExprMarker.drop()
        return true
      case _ =>
        selfInvocation parse builder
        constrExprMarker.done(ScalaElementTypes.CONSTR_EXPR)
        return true
    }
    //this line for compiler
    true
  }
}