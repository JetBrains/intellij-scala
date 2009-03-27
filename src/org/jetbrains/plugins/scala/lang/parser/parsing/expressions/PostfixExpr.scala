package org.jetbrains.plugins.scala.lang.parser.parsing.expressions

import com.intellij.lang.PsiBuilder
import lexer.ScalaTokenTypes

/**
* @author Alexander Podkhalyuzin
* Date: 03.03.2008
*/

/*
 * PostfixExpr ::= InfixExpr [id [nl]]
 */

object PostfixExpr {
  def parse(builder: PsiBuilder): Boolean = {
    val postfixMarker = builder.mark
    if (!InfixExpr.parse(builder)) {
      postfixMarker.drop
      return false
    }
    builder.getTokenType match {
      case ScalaTokenTypes.tIDENTIFIER => {
        val refMarker = builder.mark
        builder.advanceLexer //Ate id
        refMarker.done(ScalaElementTypes.REFERENCE_EXPRESSION)
        /*builder.getTokenType match {
          case ScalaTokenTypes.tLINE_TERMINATOR => {
            if (LineTerminator(builder.getTokenText)) builder.advanceLexer
          }
          case _ => {}
        }*/
        postfixMarker.done(ScalaElementTypes.POSTFIX_EXPR)
      }
      case _ => {
        postfixMarker.drop
      }
    }
    return true
  }
}