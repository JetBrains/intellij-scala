package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package types

import com.intellij.lang.PsiBuilder
import com.intellij.lang.PsiBuilder.Marker
import org.jetbrains.plugins.scala.lang.lexer.{ScalaTokenType, ScalaTokenTypes}
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.parsing.expressions.{Literal, Spliced}

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
  override protected def typeArgs: TypeArgs = TypeArgs
  override protected def types: Types = Types
}

trait SimpleType {
  protected def typeArgs: TypeArgs
  protected def types: Types

  def parse(builder: ScalaPsiBuilder, isPattern: Boolean, multipleSQBrackets: Boolean = true): Boolean = {
    @tailrec
    def parseTail(curMarker: PsiBuilder.Marker, checkSQBracket: Boolean = true): Unit = {
      builder.getTokenType match {
        case ScalaTokenTypes.tLSQBRACKET if checkSQBracket =>
          val newMarker = curMarker.precede
          typeArgs.parse(builder, isPattern)
          curMarker.done(ScalaElementType.TYPE_GENERIC_CALL)
          parseTail(newMarker, checkSQBracket = multipleSQBrackets)
        case ScalaTokenTypes.tINNER_CLASS =>
          val newMarker = curMarker.precede
          builder.advanceLexer() //Ate #
          builder.getTokenType match {
            case ScalaTokenTypes.tIDENTIFIER =>
              builder.advanceLexer() //Ate id
              curMarker.done(ScalaElementType.TYPE_PROJECTION)
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
      if (!Literal.parse(builder)) {
        curMarker.rollbackTo()
        return false
      }
      curMarker.done(ScalaElementType.LITERAL_TYPE)
      true
    }

    if (parseLiteral(builder.mark)) { //this is literal type
      return true
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
                if (isTuple) tupleMarker.done(ScalaElementType.TUPLE_TYPE)
                else {
                  builder.error("Identifier expected, but ',' found")
                  tupleMarker.done(ScalaElementType.TYPE_IN_PARENTHESIS)
                }
              case _ =>
                builder error ScalaBundle.message("rparenthesis.expected")
                if (isTuple) tupleMarker.done(ScalaElementType.TUPLE_TYPE)
                else tupleMarker.done(ScalaElementType.TYPE_IN_PARENTHESIS)
            }
          case ScalaTokenTypes.tRPARENTHESIS =>
            builder.advanceLexer() //Ate )
            if (isTuple) tupleMarker.done(ScalaElementType.TUPLE_TYPE)
            else tupleMarker.done(ScalaElementType.TYPE_IN_PARENTHESIS)
          case _ =>
            builder error ScalaBundle.message("rparenthesis.expected")
            if (isTuple) tupleMarker.done(ScalaElementType.TUPLE_TYPE)
            else tupleMarker.done(ScalaElementType.TYPE_IN_PARENTHESIS)
        }
        builder.restoreNewlinesState()
      case ScalaTokenTypes.kTHIS |
              ScalaTokenTypes.tIDENTIFIER |
              ScalaTokenTypes.kSUPER =>
        val newMarker = builder.mark
        Path parse(builder, ScalaElementType.REFERENCE)
        builder.getTokenType match {
          case ScalaTokenTypes.tDOT =>
            builder.advanceLexer() //Ate .
            builder.getTokenType match {
              case ScalaTokenTypes.kTYPE =>
                builder.advanceLexer() //Ate type
                newMarker.done(ScalaElementType.SIMPLE_TYPE)
              case _ =>
                newMarker.rollbackTo()
                val fMarker = builder.mark
                StableId parse(builder, ScalaElementType.REFERENCE)
                fMarker.done(ScalaElementType.SIMPLE_TYPE)
            }
          case _ =>
            newMarker.rollbackTo()
            val fMarker = builder.mark
            StableId parse(builder, ScalaElementType.REFERENCE)
            fMarker.done(ScalaElementType.SIMPLE_TYPE)
        }
      case ScalaTokenType.SpliceStart =>
        Spliced.parse(builder, inType = true)
      case _ =>
        return rollbackCase(builder, simpleMarker)
    }
    parseTail(simpleMarker)
    true
  }

  protected def rollbackCase(builder: ScalaPsiBuilder, simpleMarker: Marker): Boolean = {
    simpleMarker.rollbackTo()
    false
  }
}