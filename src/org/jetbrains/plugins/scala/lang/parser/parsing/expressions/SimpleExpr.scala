package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package expressions

import com.intellij.lang.PsiBuilder
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.parsing.top.ClassTemplate
import org.jetbrains.plugins.scala.lang.parser.parsing.types.{Path, TypeArgs}
import org.jetbrains.plugins.scala.lang.parser.parsing.xml.XmlExpr

import scala.annotation.tailrec

/**
  * @author Alexander Podkhalyuzin
  *         Date: 06.03.2008
  *         Time: 9:21:35
  */

/*
 * SimpleExpr ::= 'new' (ClassTemplate | TemplateBody)
 *              | BlockExpr
 *              | SimpleExpr1 ['_']
 *
 * SimpleExpr1 ::= Literal
 *               | Path
 *               | '_'
 *               | '(' [Exprs [',']] ')'
 *               | SimpleExpr '.' id
 *               | SimpleExpr TypeArgs
 *               | SimpleExpr1 ArgumentExprs
 *               | XmlExpr
 */
object SimpleExpr extends SimpleExpr {
  override protected val argumentExprs = ArgumentExprs
  override protected val classTemplate = ClassTemplate
  override protected val literal = Literal
  override protected val blockExpr = BlockExpr
  override protected val expr = Expr
}

trait SimpleExpr extends ParserNode with ScalaTokenTypes {
  protected val argumentExprs: ArgumentExprs
  protected val expr: Expr
  protected val blockExpr: BlockExpr
  protected val classTemplate: ClassTemplate
  protected val literal: Literal

  def parse(builder: ScalaPsiBuilder): Boolean = {
    val simpleMarker = builder.mark
    var newMarker: PsiBuilder.Marker = null
    var state: Boolean = false //false means SimpleExpr, true SimpleExpr1
    builder.getTokenType match {
      case ScalaTokenTypes.kNEW =>
        builder.advanceLexer() //Ate new
        if (!classTemplate.parse(builder, nonEmpty = true)) {
          builder error ErrMsg("identifier.expected")
          simpleMarker.drop()
          return false
        }
        newMarker = simpleMarker.precede
        simpleMarker.done(ScalaElementTypes.NEW_TEMPLATE)
      case ScalaTokenTypes.tLBRACE =>
        newMarker = simpleMarker.precede
        simpleMarker.drop()
        if (!blockExpr.parse(builder)) {
          newMarker.drop()
          return false
        }
      case ScalaTokenTypes.tUNDER =>
        state = true
        builder.advanceLexer() //Ate _
        newMarker = simpleMarker.precede
        simpleMarker.done(ScalaElementTypes.PLACEHOLDER_EXPR)
      case ScalaTokenTypes.tLPARENTHESIS =>
        state = true
        builder.advanceLexer()
        builder.disableNewlines
        builder.getTokenType match {
          case ScalaTokenTypes.tRPARENTHESIS =>
            builder.advanceLexer()
            builder.restoreNewlinesState
            newMarker = simpleMarker.precede
            simpleMarker.done(ScalaElementTypes.UNIT_EXPR)
          case _ =>
            if (!expr.parse(builder)) {
              builder error ErrMsg("rparenthesis.expected")
              builder.restoreNewlinesState
              newMarker = simpleMarker.precede
              simpleMarker.done(ScalaElementTypes.UNIT_EXPR)
            } else {
              var isTuple = false
              while (builder.getTokenType == ScalaTokenTypes.tCOMMA &&
                !lookAhead(builder, ScalaTokenTypes.tCOMMA, ScalaTokenTypes.tRPARENTHESIS)) {
                isTuple = true
                builder.advanceLexer()
                if (!expr.parse(builder)) {
                  builder error ErrMsg("wrong.expression")
                }
              }
              if (builder.getTokenType == ScalaTokenTypes.tCOMMA) {
                builder.advanceLexer()
                isTuple = true
              }
              if (builder.getTokenType != ScalaTokenTypes.tRPARENTHESIS) {
                builder error ErrMsg("rparenthesis.expected")
              } else {
                builder.advanceLexer()
              }
              builder.restoreNewlinesState
              newMarker = simpleMarker.precede
              simpleMarker.done(if (isTuple) ScalaElementTypes.TUPLE else ScalaElementTypes.PARENT_EXPR)
            }
        }
      case _ =>
        state = true
        if (!literal.parse(builder)) {
          if (!XmlExpr.parse(builder)) {
            if (!Path.parse(builder, ScalaElementTypes.REFERENCE_EXPRESSION)) {
              simpleMarker.drop()
              return false
            }
          }
        }
        newMarker = simpleMarker.precede
        simpleMarker.drop()
    }
    @tailrec
    def subparse(marker: PsiBuilder.Marker) {
      builder.getTokenType match {
        case ScalaTokenTypes.tUNDER if !builder.newlineBeforeCurrentToken =>
          if (state) {
            builder.advanceLexer()
            val tMarker = marker.precede
            marker.done(ScalaElementTypes.PLACEHOLDER_EXPR)
            subparse(tMarker)
          }
          else {
            marker.drop()
          }
        case ScalaTokenTypes.tDOT =>
          state = true
          builder.advanceLexer() //Ate .
          builder.getTokenType match {
            case ScalaTokenTypes.tIDENTIFIER =>
              builder.advanceLexer() //Ate id
            val tMarker = marker.precede
              marker.done(ScalaElementTypes.REFERENCE_EXPRESSION)
              subparse(tMarker)
            case _ =>
              builder error ScalaBundle.message("identifier.expected")
              marker.drop()
          }
        case ScalaTokenTypes.tLPARENTHESIS | ScalaTokenTypes.tLBRACE if
        builder.getTokenType != ScalaTokenTypes.tLPARENTHESIS || !builder.newlineBeforeCurrentToken =>
          if (state && argumentExprs.parse(builder)) {
            val tMarker = marker.precede
            marker.done(ScalaElementTypes.METHOD_CALL)
            subparse(tMarker)
          }
          else {
            marker.drop()
          }
        case ScalaTokenTypes.tLSQBRACKET =>
          state = true
          TypeArgs.parse(builder, isPattern = false)
          val tMarker = marker.precede
          marker.done(ScalaElementTypes.GENERIC_CALL)
          subparse(tMarker)
        case _ =>
          marker.drop()
      }
    }
    subparse(newMarker)
    return true
  }
}