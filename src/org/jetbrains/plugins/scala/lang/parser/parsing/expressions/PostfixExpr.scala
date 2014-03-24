package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package expressions

import com.intellij.lang.PsiBuilder
import lexer.ScalaTokenTypes
import builder.ScalaPsiBuilder

/**
* @author Alexander Podkhalyuzin
* Date: 03.03.2008
*/

/*
 * PostfixExpr ::= InfixExpr [id [nl]]
 */

object PostfixExpr {
  def parse(builder: ScalaPsiBuilder, isPattern: Boolean = false): Boolean = {
    val postfixMarker = builder.mark
    if (!InfixExpr.parse(builder, isPattern)) {
      postfixMarker.drop
      return false
    }
    builder.getTokenType match {
      case ScalaTokenTypes.tIDENTIFIER if !builder.newlineBeforeCurrentToken => {
        val refMarker = builder.mark
        builder.advanceLexer //Ate id
        refMarker.done(if (isPattern) ScalaElementTypes.REFERENCE_PATTERN else ScalaElementTypes.REFERENCE_EXPRESSION)
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