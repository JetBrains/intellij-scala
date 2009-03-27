package org.jetbrains.plugins.scala.lang.parser.parsing.expressions

import base.Constructor
import com.intellij.lang.PsiBuilder
import lexer.ScalaTokenTypes
import nl.LineTerminator

/**
* @author Alexander Podkhalyuzin
* Date: 06.03.2008
*/

/*
 * AnnotationExpr ::= Constr [[nl] '{' {NameValuePair} '}']
 */

object AnnotationExpr {
  def parse(builder: PsiBuilder): Boolean = {
    val annotExprMarker = builder.mark
    if (!Constructor.parse(builder, true)) {
      annotExprMarker.drop
      return false
    }
    val rollbackMarker = builder.mark
    builder.getTokenType match {
      case ScalaTokenTypes.tLINE_TERMINATOR => {
        if (LineTerminator(builder.getTokenText)) {
          builder.advanceLexer
        }
        else {
          rollbackMarker.drop
          annotExprMarker.done(ScalaElementTypes.ANNOTATION_EXPR)
          return true
        }
      }
      case _ =>
    }
    builder.getTokenType match {
      case ScalaTokenTypes.tLBRACE => {
        builder.advanceLexer //Ate }
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
        builder.getTokenType match {
          case ScalaTokenTypes.tRBRACE => {
            builder.advanceLexer
          }
          case _ => {
            builder error ScalaBundle.message("rbrace.expected")
          }
        }
        rollbackMarker.drop
        annotExprMarker.done(ScalaElementTypes.ANNOTATION_EXPR)
        return true
      }
      case _ => {
        rollbackMarker.rollbackTo
        annotExprMarker.done(ScalaElementTypes.ANNOTATION_EXPR)
        return true
      }
    }
  }
}