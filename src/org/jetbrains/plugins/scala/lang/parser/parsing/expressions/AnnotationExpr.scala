package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package expressions

import base.Constructor
import com.intellij.lang.PsiBuilder
import lexer.ScalaTokenTypes
import nl.LineTerminator
import builder.ScalaPsiBuilder
import util.ParserUtils

/**
* @author Alexander Podkhalyuzin
* Date: 06.03.2008
*/

/*
 * AnnotationExpr ::= Constr [[nl] '{' {NameValuePair} '}']
 */

object AnnotationExpr {
  def parse(builder: ScalaPsiBuilder): Boolean = {
    val annotExprMarker = builder.mark
    if (!Constructor.parse(builder, true)) {
      annotExprMarker.drop()
      return false
    }
    builder.getTokenType match {
      case ScalaTokenTypes.tLBRACE => {
        if (builder.countNewlineBeforeCurrentToken > 1) {
          annotExprMarker.done(ScalaElementTypes.ANNOTATION_EXPR)
          return true
        }
        builder.advanceLexer //Ate }
        builder.enableNewlines
        def foo() =
          while (NameValuePair.parse(builder)) {
            builder.getTokenType match {
              case ScalaTokenTypes.tCOMMA => builder.advanceLexer
              case _ =>
            }
            while (builder.getTokenType == ScalaTokenTypes.tCOMMA) {
              builder.error(ScalaBundle.message("wrong.annotation.expression"))
              builder.advanceLexer
            }
          }
        ParserUtils.parseLoopUntilRBrace(builder, foo _)
        builder.restoreNewlinesState
        annotExprMarker.done(ScalaElementTypes.ANNOTATION_EXPR)
        return true
      }
      case _ => {
        annotExprMarker.done(ScalaElementTypes.ANNOTATION_EXPR)
        return true
      }
    }
  }
}