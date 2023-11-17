package org.jetbrains.plugins.scala.lang.parser.parsing.types

import com.intellij.lang.PsiBuilder
import com.intellij.lang.PsiBuilder.Marker
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.lang.lexer.{ScalaTokenType, ScalaTokenTypes}
import org.jetbrains.plugins.scala.lang.parser.ScalaElementType
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.parsing.expressions.{Literal, SplicedType}

import scala.annotation.tailrec

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

  final def apply(isPattern: Boolean, multipleSQBrackets: Boolean = true)(implicit builder: ScalaPsiBuilder): Boolean = {
    @tailrec
    def parseTail(curMarker: PsiBuilder.Marker, checkSQBracket: Boolean = true): Unit = {
      builder.getTokenType match {
        case ScalaTokenTypes.tLSQBRACKET if checkSQBracket =>
          val newMarker = curMarker.precede
          typeArgs(isPattern)
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
      if (!Literal()) {
        curMarker.rollbackTo()
        return false
      }
      curMarker.done(ScalaElementType.LITERAL_TYPE)
      true
    }

    if (parseLiteral(builder.mark())) { //this is literal type
      return true
    }
    val simpleMarker = builder.mark()
    builder.getTokenType match {
      case ScalaTokenTypes.tLPARENTHESIS =>
        val tupleMarker = builder.mark()
        builder.advanceLexer()
        builder.disableNewlines()
        val (_, isTuple) = types(isPattern, typeVariables = true)
        builder.getTokenType match {
          case ScalaTokenTypes.tCOMMA =>
            builder.advanceLexer() //Ate ,
            builder.getTokenType match {
              case ScalaTokenTypes.tRPARENTHESIS =>
                builder.advanceLexer() //Ate )
                if (isTuple) tupleMarker.done(ScalaElementType.TUPLE_TYPE)
                else {
                  builder.error(ScalaBundle.message("identifier.expected.comma.found"))
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
      case ScalaTokenTypes.tUNDER if builder.underscoreWildcardsDisabled =>
        val simpleTypeMarker = builder.mark()
        val refMarker = builder.mark()
        builder.remapCurrentToken(ScalaTokenTypes.tIDENTIFIER)
        builder.advanceLexer()
        refMarker.done(ScalaElementType.REFERENCE)
        simpleTypeMarker.done(ScalaElementType.SIMPLE_TYPE)
      case ScalaTokenTypes.kTHIS |
           ScalaTokenTypes.tIDENTIFIER |
           ScalaTokenTypes.kSUPER =>
        val newMarker = builder.mark()
        val refMarker = builder.mark()
        if (builder.underscoreWildcardsDisabled
          && (builder.getTokenText == "+" || builder.getTokenText == "-")) {
          builder.advanceLexer()
          if (builder.getTokenText == "_") {
            builder.advanceLexer()
            refMarker.collapse(ScalaTokenTypes.tIDENTIFIER)
            newMarker.done(ScalaElementType.REFERENCE)
            simpleMarker.done(ScalaElementType.SIMPLE_TYPE)
            return true
          } else newMarker.rollbackTo()
        } else refMarker.drop()

        Path(ScalaElementType.REFERENCE)
        builder.getTokenType match {
          case ScalaTokenTypes.tDOT =>
            builder.advanceLexer() //Ate .
            builder.getTokenType match {
              case ScalaTokenTypes.kTYPE =>
                builder.advanceLexer() //Ate type
                newMarker.done(ScalaElementType.SIMPLE_TYPE)
              case _ =>
                newMarker.rollbackTo()
                val fMarker = builder.mark()
                StableId(ScalaElementType.REFERENCE)
                fMarker.done(ScalaElementType.SIMPLE_TYPE)
            }
          case _ =>
            newMarker.rollbackTo()
            val fMarker = builder.mark()
            StableId(ScalaElementType.REFERENCE)
            fMarker.done(ScalaElementType.SIMPLE_TYPE)
        }
      case ScalaTokenType.SpliceStart =>
        SplicedType()
      case ScalaTokenTypes.tLBRACE if builder.isScala3 =>
        // in scala 2 refinement is handled in CompoundType
        val compoundMarker = builder.mark()
        if (Refinement()) {
          compoundMarker.done(ScalaElementType.COMPOUND_TYPE)
        } else {
          compoundMarker.drop()
        }
      case _ =>
        return rollbackCase(simpleMarker)
    }
    parseTail(simpleMarker)
    true
  }

  protected def rollbackCase(simpleMarker: Marker): Boolean = {
    simpleMarker.rollbackTo()
    false
  }
}
