package org.jetbrains.plugins.scala.lang.parser.parsing.types

import com.intellij.lang.PsiBuilder, org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.parser.bnf.BNF
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.lang.parser.util.ParserUtils
import org.jetbrains.plugins.scala.lang.lexer.ScalaElementType
import org.jetbrains.plugins.scala.lang.parser.parsing.expressions.InfixTemplate
import org.jetbrains.plugins.scala.ScalaBundle

/**
* Created by IntelliJ IDEA.
* User: Alexander.Podkhalyuz
* Date: 15.02.2008
* Time: 17:37:29
* To change this template use File | Settings | File Templates.
*/

/*
 * SimpleType ::= SimpleType TypeArgs
 *              | SimpleType '#' id
 *              | StableId
 *              | Path '.' 'type'
 *              | '(' Types [','] ')'
 */

object SimpleType {
  def parse(builder: PsiBuilder): Boolean = {
    def parseTale(curMarker: PsiBuilder.Marker) {
      builder.getTokenType match {
        case ScalaTokenTypes.tLSQBRACKET => {
          val newMarker = curMarker.precede
          curMarker.done(ScalaElementTypes.SIMPLE_TYPE)
          TypeArgs parse builder
          parseTale(newMarker)
        }
        case ScalaTokenTypes.tINNER_CLASS => {
          val newMarker = curMarker.precede
          curMarker.done(ScalaElementTypes.SIMPLE_TYPE)
          builder.advanceLexer //Ate #
          builder.getTokenType match {
            case ScalaTokenTypes.tIDENTIFIER => {
              builder.advanceLexer //Ate id
              parseTale(newMarker)
            }
            case _ => {
              builder error ScalaBundle.message("identifier.expected",new Array[Object](0))
              parseTale(newMarker)
            }
          }
        }
        case _ => {
          curMarker.done(ScalaElementTypes.SIMPLE_TYPE)
        }
      }
    }
    val simpleMarker = builder.mark
    builder.getTokenType match {
      case ScalaTokenTypes.tLPARENTHESIS => {
        val tupleMarker = builder.mark
        builder.advanceLexer
        Types parse builder
        builder.getTokenType match {
          case ScalaTokenTypes.tCOMMA => {
            builder.advanceLexer //Ate ,
            builder.getTokenType match {
              case ScalaTokenTypes.tRPARENTHESIS => {
                builder.advanceLexer //Ate )
                tupleMarker.done(ScalaElementTypes.TUPLE)
              }
              case _ => {
                builder error ScalaBundle.message("rparenthesis.expacted", new Array[Object](0))
                tupleMarker.done(ScalaElementTypes.TUPLE)
              }
            }
          }
          case ScalaTokenTypes.tRPARENTHESIS => {
            builder.advanceLexer //Ate )
            tupleMarker.done(ScalaElementTypes.TUPLE)
          }
          case _ => {
            builder error ScalaBundle.message("rparenthesis.expacted", new Array[Object](0))
            tupleMarker.done(ScalaElementTypes.TUPLE)
          }
        }
      }
      case ScalaTokenTypes.kTHIS | ScalaTokenTypes.tIDENTIFIER => {
        val newMarker = builder.mark
        Path parse (builder,true)
        builder.getTokenType match {
          case ScalaTokenTypes.tDOT => {
            builder.advanceLexer //Ate .
            builder.getTokenType match {
              case ScalaTokenTypes.kTYPE => {
                builder.advanceLexer //Ate type
                newMarker.drop
              }
              case _ => {
                newMarker.rollbackTo
                StableId parse builder
              }
            }
          }
          case _ => {
            newMarker.rollbackTo
            StableId parse builder
          }
        }
      }
      case _ => {
        simpleMarker.rollbackTo
        return false
      }
    }
    parseTale(simpleMarker)
    return true
  }
}