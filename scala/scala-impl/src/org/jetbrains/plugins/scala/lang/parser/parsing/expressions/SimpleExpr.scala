package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package expressions

import com.intellij.lang.PsiBuilder
import org.jetbrains.plugins.scala.lang.parser.parsing.base.End
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.parsing.statements.Def
import org.jetbrains.plugins.scala.lang.parser.parsing.top.ClassTemplateBlock
import org.jetbrains.plugins.scala.lang.parser.parsing.types.{Path, TypeArgs}
import org.jetbrains.plugins.scala.lang.parser.parsing.xml.XmlExpr
import org.jetbrains.plugins.scala.lang.parser.util.ParserUtils

import scala.annotation.tailrec

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
object SimpleExpr extends ParsingRule {

  import lexer.ScalaTokenType._
  import lexer.ScalaTokenTypes._

  override def parse(implicit builder: ScalaPsiBuilder): Boolean = {
    val simpleMarker = builder.mark()
    var newMarker: PsiBuilder.Marker = null
    var state: Boolean = false //false means SimpleExpr, true SimpleExpr1
    builder.getTokenType match {
      case NewKeyword =>
        val iw = builder.currentIndentationWidth
        builder.advanceLexer() //Ate new
        if (!ClassTemplateBlock()) {
          builder error ErrMsg("identifier.expected")
          simpleMarker.drop()
          return false
        }
        newMarker = simpleMarker.precede
        End(iw)
        simpleMarker.done(ScalaElementType.NewTemplate)
      case `tLBRACE` =>
        newMarker = simpleMarker.precede
        simpleMarker.drop()
        if (!BlockExpr()) {
          newMarker.drop()
          return false
        }
      case `tUNDER` =>
        state = true
        builder.advanceLexer() //Ate _
        newMarker = simpleMarker.precede
        simpleMarker.done(ScalaElementType.PLACEHOLDER_EXPR)
      case `SpliceStart` =>
        newMarker = simpleMarker.precede
        simpleMarker.drop()
        SplicedExpr()
      case `QuoteStart` =>
        newMarker = simpleMarker.precede
        simpleMarker.drop()
        Quoted()
      case `tLPARENTHESIS` =>
        state = true
        builder.advanceLexer()
        builder.disableNewlines()
        builder.getTokenType match {
          case `tRPARENTHESIS` =>
            builder.advanceLexer()
            builder.restoreNewlinesState()
            newMarker = simpleMarker.precede
            simpleMarker.done(ScalaElementType.UNIT_EXPR)
          case _ =>
            if (!Expr()) {
              builder error ErrMsg("rparenthesis.expected")
              builder.restoreNewlinesState()
              newMarker = simpleMarker.precede
              simpleMarker.done(ScalaElementType.UNIT_EXPR)
            } else {
              var isTuple = false
              while (builder.getTokenType == tCOMMA &&
                !builder.lookAhead(tCOMMA, tRPARENTHESIS)) {
                isTuple = true
                builder.advanceLexer()
                if (!Expr()) {
                  builder error ErrMsg("wrong.expression")
                }
              }
              if (builder.getTokenType == tCOMMA && !builder.consumeTrailingComma(tRPARENTHESIS)) {
                builder.advanceLexer()
                isTuple = true
              }
              if (builder.getTokenType != tRPARENTHESIS) {
                builder error ErrMsg("rparenthesis.expected")
              } else {
                builder.advanceLexer()
              }
              builder.restoreNewlinesState()
              newMarker = simpleMarker.precede
              simpleMarker.done(if (isTuple) ScalaElementType.TUPLE else ScalaElementType.PARENT_EXPR)
            }
        }
      case _ =>
        state = true
        if (!Literal()) {
          if (!XmlExpr()) {
            if (!Path(ScalaElementType.REFERENCE_EXPRESSION)) {
              simpleMarker.drop()
              return false
            }
          }
        }
        newMarker = simpleMarker.precede
        simpleMarker.drop()
    }
    @tailrec
    def subparse(marker: PsiBuilder.Marker): Unit = {
      builder.getTokenType match {
        case `tUNDER` if !builder.newlineBeforeCurrentToken =>
          if (state) {
            builder.advanceLexer()
            val tMarker = marker.precede
            marker.done(ScalaElementType.PLACEHOLDER_EXPR)
            subparse(tMarker)
          }
          else {
            marker.drop()
          }
        case `tDOT` =>
          state = true
          builder.advanceLexer() //Ate .
          builder.getTokenType match {
            case `tIDENTIFIER` =>
              builder.advanceLexer() //Ate id
            val tMarker = marker.precede
              marker.done(ScalaElementType.REFERENCE_EXPRESSION)
              subparse(tMarker)
            case `kMATCH` =>
              val tMarker = marker.precede()
              Expr1.parseMatch(marker)
              subparse(tMarker)
            case _ =>
              builder error ScalaBundle.message("identifier.expected")
              marker.drop()
          }
        case `tLPARENTHESIS` | `tLBRACE` if
        builder.getTokenType != tLPARENTHESIS || !builder.newlineBeforeCurrentToken =>
          if (state && ArgumentExprs()) {
            val tMarker = marker.precede
            marker.done(ScalaElementType.METHOD_CALL)
            subparse(tMarker)
          }
          else {
            marker.drop()
          }
        case `tLSQBRACKET` =>
          state = true
          TypeArgs(isPattern = false)
          val tMarker = marker.precede
          marker.done(ScalaElementType.GENERIC_CALL)
          subparse(tMarker)
        case `kDEF` | `kPRIVATE` | `kPROTECTED` | `kIMPLICIT` if ParserUtils.hasTextBefore(builder, "inline") =>
          //This is kinda hack for cases when we have to build stubs for sources, that use meta and contain inline keyword
          //Without this we would get different count of stub elements and ast nodes (and exception as the result)  
          marker.drop()
          Def()
        case _ =>
          marker.drop()
      }
    }
    subparse(newMarker)
    
    true
  }
}