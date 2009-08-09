package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package statements

import com.intellij.lang.PsiBuilder
import expressions.SelfInvocation
import lexer.ScalaTokenTypes

/**
* @author Alexander Podkhalyuzin
* Date: 13.02.2008
*/

/*
 * ConstrExpr ::= SelfInvocation
 *              | '{' SelfInvocation {semi BlockStat} '}'
 */

object ConstrExpr {
  def parse(builder: PsiBuilder): Boolean = {
    val constrExprMarker = builder.mark
    builder.getTokenType match {
      case ScalaTokenTypes.tLBRACE => {
        ConstrBlock parse builder
        constrExprMarker.drop
        return true
      }
      case _ => {
        SelfInvocation parse builder
        constrExprMarker.done(ScalaElementTypes.CONSTR_EXPR)
        return true
      }
    }
    //this line for compiler
    return true
  }
}