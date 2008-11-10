package org.jetbrains.plugins.scala.lang.parser.parsing.expressions

import com.intellij.lang.PsiBuilder
import lexer.ScalaTokenTypes
import patterns.CaseClauses

/**
* @author Alexander Podkhalyuzin
* Date: 06.03.2008
*/

/*
 * BlockExpr ::= '{' CaseClauses '}'
 *             | '{' Block '}'
 */

object BlockExpr {
  def parse(builder: PsiBuilder): Boolean = {
    val blockExprMarker = builder.mark
    builder.getTokenType match {
      case ScalaTokenTypes.tLBRACE => {
        builder.advanceLexer
      }
      case _ => {
        blockExprMarker.drop
        return false
      }
    }
    builder.getTokenType match {
      case ScalaTokenTypes.kCASE => {
        val backMarker = builder.mark
        builder.advanceLexer
        builder.getTokenType match {
          case ScalaTokenTypes.kCLASS |
               ScalaTokenTypes.kOBJECT => {
             backMarker.rollbackTo
            Block parse builder
          }
          case _ => {
            backMarker.rollbackTo
            CaseClauses parse builder
          }
        }
      }
      case _ => {
        Block parse builder
      }
    }
    builder.getTokenType match {
      case ScalaTokenTypes.tRBRACE => {
        builder.advanceLexer //Ate }
      }
      case _ => {
        builder error ScalaBundle.message("rbrace.expected")
      }
    }
    blockExprMarker.done(ScalaElementTypes.BLOCK_EXPR)
    return true
  }
}