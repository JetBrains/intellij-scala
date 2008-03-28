package org.jetbrains.plugins.scala.lang.parser.parsing.types

import com.intellij.lang.PsiBuilder, org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.parser.bnf.BNF
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.lang.parser.util.ParserUtils
import org.jetbrains.plugins.scala.lang.lexer.ScalaElementType
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.lang.parser.parsing.nl.LineTerminator

/** 
* @author Alexander Podkhalyuzin
* Date: 28.02.2008
*/

/*
 * Type ::= InfixType '=>' Type
 *        | '(' ['=>' Type] ')' => Type
 *        | InfixType [ExistentialClause]
 */

object Type {
  def parse(builder: PsiBuilder): Boolean = parse(builder,false)

  def parse(builder: PsiBuilder,star: Boolean): Boolean = {
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
            parMarker.drop
            typeMarker.done(ScalaElementTypes.TYPE)
            return true
          }
          case _ => {parMarker.rollbackTo}
        }
      }
      case _ => {}
    }
    if (!InfixType.parse(builder,star)) {
      typeMarker.drop
      return false
    }
    builder.getTokenType match {
      case ScalaTokenTypes.tFUNTYPE => {
        builder.advanceLexer //Ate =>
        if (!Type.parse(builder)) {
          builder error ScalaBundle.message("wrong.type", new Array[Object](0))
        }
        typeMarker.done(ScalaElementTypes.TYPE)
        return true
      }
      case ScalaTokenTypes.kFOR_SOME => {
        ExistentialClause parse builder
        typeMarker.done(ScalaElementTypes.EXISTENTIAL_TYPE)
        return true
      }
      case _ => {
        typeMarker.drop
        return true
      }
    }
  }
}