package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package types

import com.intellij.lang.PsiBuilder
import com.intellij.lang.PsiBuilder.Marker
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.parsing.expressions.Literal

import scala.annotation.tailrec

/**
* @author Alexander Podkhalyuzin
* Date: 15.02.2008
*/

/*
 * SimpleType ::= SimpleType TypeArgs
 *              | SimpleType '#' id
 *              | StableId
 *              | Path '.' 'type'
 *              | '(' Types [','] ')'
 */
object SimpleType extends SimpleType {
  override protected def typeArgs = TypeArgs
  override protected def types = Types
  override protected def literal = Literal
}

trait SimpleType {
  protected def typeArgs: TypeArgs
  protected def types: Types
  protected def literal: Literal

  def parse(builder: ScalaPsiBuilder, isPattern: Boolean, multipleSQBrackets: Boolean = true): Boolean = {
    @tailrec
    def parseTail(curMarker: PsiBuilder.Marker, checkSQBracket: Boolean = true) {
      builder.getTokenType match {
        case ScalaTokenTypes.tLSQBRACKET if checkSQBracket =>
          val newMarker = curMarker.precede
          typeArgs.parse(builder, isPattern)
          curMarker.done(ScalaElementTypes.TYPE_GENERIC_CALL)
          parseTail(newMarker, checkSQBracket = multipleSQBrackets)
        case ScalaTokenTypes.tINNER_CLASS =>
          val newMarker = curMarker.precede
          builder.advanceLexer() //Ate #
          builder.getTokenType match {
            case ScalaTokenTypes.tIDENTIFIER =>
              builder.advanceLexer() //Ate id
              curMarker.done(ScalaElementTypes.TYPE_PROJECTION)
              parseTail(newMarker)
            case _ =>
              newMarker.drop()
              curMarker.drop()
          }
        case _ =>
          curMarker.drop()
      }
    }
    def parseLiteral(curMarker: PsiBuilder.Marker): Boolean = {
      curMarker.drop()
      val newMarker = builder.mark
      if (!literal.parse(builder)) {
        newMarker.rollbackTo()
        return false
      }
      newMarker.done(ScalaElementTypes.LITERAL_TYPE)
      true
    }

    val simpleMarker = builder.mark
    builder.getTokenType match {
      case ScalaTokenTypes.tLPARENTHESIS =>
        val tupleMarker = builder.mark
        builder.advanceLexer()
        builder.disableNewlines()
        val (_, isTuple) = types parse builder
        builder.getTokenType match {
          case ScalaTokenTypes.tCOMMA =>
            builder.advanceLexer() //Ate ,
            builder.getTokenType match {
              case ScalaTokenTypes.tRPARENTHESIS =>
                builder.advanceLexer() //Ate )
                if (isTuple) tupleMarker.done(ScalaElementTypes.TUPLE_TYPE)
                else {
                  builder.error("Identifier expected, but ',' found")
                  tupleMarker.done(ScalaElementTypes.TYPE_IN_PARENTHESIS)
                }
              case _ =>
                builder error ScalaBundle.message("rparenthesis.expected")
                if (isTuple) tupleMarker.done(ScalaElementTypes.TUPLE_TYPE)
                else tupleMarker.done(ScalaElementTypes.TYPE_IN_PARENTHESIS)
            }
          case ScalaTokenTypes.tRPARENTHESIS =>
            builder.advanceLexer() //Ate )
            if (isTuple) tupleMarker.done(ScalaElementTypes.TUPLE_TYPE)
            else tupleMarker.done(ScalaElementTypes.TYPE_IN_PARENTHESIS)
          case _ =>
            builder error ScalaBundle.message("rparenthesis.expected")
            if (isTuple) tupleMarker.done(ScalaElementTypes.TUPLE_TYPE)
            else tupleMarker.done(ScalaElementTypes.TYPE_IN_PARENTHESIS)
        }
        builder.restoreNewlinesState()
      case ScalaTokenTypes.tIDENTIFIER if builder.getTokenText == "-" => return parseLiteral(simpleMarker)
      case ScalaTokenTypes.kTHIS |
              ScalaTokenTypes.tIDENTIFIER |
              ScalaTokenTypes.kSUPER =>
        val newMarker = builder.mark
        Path parse (builder, ScalaElementTypes.REFERENCE)
        builder.getTokenType match {
          case ScalaTokenTypes.tDOT =>
            builder.advanceLexer() //Ate .
            builder.getTokenType match {
              case ScalaTokenTypes.kTYPE =>
                builder.advanceLexer() //Ate type
                newMarker.done(ScalaElementTypes.SIMPLE_TYPE)
              case _ =>
                newMarker.rollbackTo()
                val fMarker = builder.mark
                StableId parse (builder, ScalaElementTypes.REFERENCE)
                fMarker.done(ScalaElementTypes.SIMPLE_TYPE)
            }
          case _ =>
            newMarker.rollbackTo()
            val fMarker = builder.mark
            StableId parse (builder, ScalaElementTypes.REFERENCE)
            fMarker.done(ScalaElementTypes.SIMPLE_TYPE)
        }
      case ScalaTokenTypes.tINTEGER | ScalaTokenTypes.tFLOAT | ScalaTokenTypes.kTRUE | ScalaTokenTypes.kFALSE |
           ScalaTokenTypes.tCHAR | ScalaTokenTypes.tSYMBOL => return parseLiteral(simpleMarker)
      case tokenType if ScalaTokenTypes.STRING_LITERAL_TOKEN_SET.contains(tokenType) => return parseLiteral(simpleMarker)
      case _ => return rollbackCase(builder, simpleMarker)
    }
    parseTail(simpleMarker)
    true
  }

  protected def rollbackCase(builder: ScalaPsiBuilder, simpleMarker: Marker): Boolean = {
    simpleMarker.rollbackTo()
    false
  }
}