package org.jetbrains.plugins.scala.lang.parser.parsing.patterns

import com.intellij.lang.PsiBuilder, org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.parser.bnf.BNF
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.lang.parser.util.ParserUtils
import org.jetbrains.plugins.scala.lang.lexer.ScalaElementType
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.lang.parser.parsing.nl.LineTerminator
import org.jetbrains.plugins.scala.lang.parser.parsing.types._

/** 
* Created by IntelliJ IDEA.
* User: Alexander.Podkhalyuz
* Date: 29.02.2008
* Time: 15:04:23
* To change this template use File | Settings | File Templates.
*/

/*
 * TypePattern ::= Type (but it can't be InfixType => Type (because case A => B => C?))
 */

object TypePattern {
  def parse(builder: PsiBuilder): Boolean = {
    val typeMarker = builder.mark
    builder.getTokenType match {
      case ScalaTokenTypes.tLPARENTHESIS => {
        val parMarker = builder.mark
        builder.advanceLexer //Ate (
        builder.getTokenType match {
          case ScalaTokenTypes.tFUNTYPE | ScalaTokenTypes.tRPARENTHESIS => {
            if (builder.getTokenType == ScalaTokenTypes.tFUNTYPE) {
              builder.advanceLexer //Ate =>
              if (!Type.parse(builder)) {
                builder error ScalaBundle.message("wrong.type", new Array[Object](0))
              }
            }
            builder.getTokenType match {
              case ScalaTokenTypes.tRPARENTHESIS => {
                builder.advanceLexer //Ate )
              }
              case _ => {
                builder error ScalaBundle.message("rparenthesis.expected", new Array[Object](0))
              }
            }
            builder.getTokenType match {
              case ScalaTokenTypes.tFUNTYPE => {
                builder.advanceLexer //Ate =>
              }
              case _ => {
                builder error ScalaBundle.message("fun.sign.expected", new Array[Object](0))
              }
            }
            if (!Type.parse(builder)) {
              builder error ScalaBundle.message("wrong.type", new Array[Object](0))
            }
            typeMarker.done(ScalaElementTypes.TYPE_PATTERN)
            parMarker.drop
            return true
          }
          case _ => {parMarker.rollbackTo}
        }
      }
      case _ => {}
    }
    if (!InfixType.parse(builder)) {
      typeMarker.drop
      return false
    }
    builder.getTokenType match {
      case ScalaTokenTypes.kFOR_SOME => {
        ExistentialClause parse builder
        typeMarker.done(ScalaElementTypes.TYPE_PATTERN)
        return true
      }
      case _ => {
        typeMarker.done(ScalaElementTypes.TYPE_PATTERN)
        return true
      }
    }
  }
}