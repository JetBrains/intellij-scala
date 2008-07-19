package org.jetbrains.plugins.scala.lang.parser.parsing.expressions

import com.intellij.lang.PsiBuilder, org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.lexer.ScalaElementType
import org.jetbrains.plugins.scala.lang.parser.bnf.BNF
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.tree.IElementType

import com.intellij.psi.PsiFile
import com.intellij.lang.ParserDefinition

import org.jetbrains.plugins.scala.lang.parser.util.ParserUtils
import org.jetbrains.plugins.scala.lang.parser.parsing.types._
import org.jetbrains.plugins.scala.lang.parser.parsing.nl.LineTerminator
import org.jetbrains.plugins.scala.lang.parser.parsing.patterns._
import org.jetbrains.plugins.scala.lang.parser.parsing.top.ClassTemplate
import org.jetbrains.plugins.scala.ScalaFileType

import org.jetbrains.plugins.scala.util.DebugPrint
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl

import com.intellij.openapi.util.TextRange

import com.intellij.lang.ASTNode
import com.intellij.psi.impl.source.tree.CompositeElement
import com.intellij.util.CharTable
import com.intellij.lexer.Lexer
import com.intellij.lang.impl.PsiBuilderImpl

//import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import com.intellij.psi._
import com.intellij.psi.impl.source.CharTableImpl
import org.jetbrains.plugins.scala.lang.lexer._
import org.jetbrains.plugins.scala.lang.parser.parsing.xml.XmlExpr

/** 
* @author Alexander Podkhalyuzin
* Date: 06.03.2008
* Time: 9:21:35
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
 *               | XmlExpr //Todo: xmlExpression
 */

object SimpleExpr extends ParserNode with ScalaTokenTypes {
  def parse(builder: PsiBuilder): Boolean = {
    var simpleMarker = builder.mark
    var newMarker: PsiBuilder.Marker = null
    var state: Boolean = false //false means SimpleExpr, true SimpleExpr1
    builder.getTokenType match {
      case ScalaTokenTypes.kNEW => {
        builder.advanceLexer //Ate new
        ClassTemplate parse builder
        newMarker = simpleMarker.precede
        simpleMarker.done(ScalaElementTypes.NEW_TEMPLATE)
      }
      case ScalaTokenTypes.tLBRACE => {
        newMarker = simpleMarker.precede
        simpleMarker.drop
        if (!BlockExpr.parse(builder)) {
          newMarker.drop
          return false
        }
      }
      case ScalaTokenTypes.tUNDER => {
        state = true
        builder.advanceLexer //Ate _
        newMarker = simpleMarker.precede
        simpleMarker.done(ScalaElementTypes.SIMPLE_EXPR)
      }
      case ScalaTokenTypes.tLPARENTHESIS => {
        state = true
        builder.advanceLexer
        builder.getTokenType match {
          case ScalaTokenTypes.tRPARENTHESIS => {
            builder.advanceLexer
            newMarker = simpleMarker.precede
            simpleMarker.done(ScalaElementTypes.UNIT_EXPR)
          }
          case _ => {
            if (!Expr.parse(builder)) {
              builder error ErrMsg("rparenthesis.expected")
              newMarker = simpleMarker.precede
              simpleMarker.done(ScalaElementTypes.UNIT_EXPR)
            } else {
              var isTuple = false
              while (builder.getTokenType == ScalaTokenTypes.tCOMMA &&
                !lookAhead(builder, ScalaTokenTypes.tCOMMA, ScalaTokenTypes.tRPARENTHESIS)) {
                isTuple = true
                builder.advanceLexer
                if (!Expr.parse(builder)) {
                  builder error ErrMsg("wrong.expression")
                }
              }
              if (builder.getTokenType == ScalaTokenTypes.tCOMMA) {
                builder.advanceLexer
                isTuple = true
              }
              if (builder.getTokenType != ScalaTokenTypes.tRPARENTHESIS) {
                builder error ErrMsg("rparenthesis.expected")
              } else {
                builder.advanceLexer
              }
              newMarker = simpleMarker.precede
              simpleMarker.done(if (isTuple) ScalaElementTypes.TUPLE else ScalaElementTypes.PARENT_EXPR)
            }
          }
        }
      }
      case _ => {
        state = true
        if (!Literal.parse(builder)){
          if (!XmlExpr.parse(builder)) {
            if (!Path.parse(builder, ScalaElementTypes.REFERENCE_EXPRESSION)) {
              simpleMarker.drop
              return false
            }
          }
        }
        newMarker = simpleMarker.precede
        simpleMarker.drop
      }
    }
    def subparse(marker: PsiBuilder.Marker) {
      builder.getTokenType match {
        case ScalaTokenTypes.tUNDER => {
          if (state) {
            builder.advanceLexer
            val tMarker = marker.precede
            marker.done(ScalaElementTypes.SIMPLE_EXPR)
            subparse(tMarker)
          }
          else {
            marker.drop
          }
        }
        case ScalaTokenTypes.tDOT => {
          state = true
          builder.advanceLexer //Ate .
          builder.getTokenType match {
            case ScalaTokenTypes.tIDENTIFIER => {
              builder.advanceLexer //Ate id
            }
            case _ => {
              builder error ScalaBundle.message("identifier.expected", new Array[Object](0))
            }
          }
          val tMarker = marker.precede
          marker.done(ScalaElementTypes.REFERENCE_EXPRESSION)
          subparse(tMarker)
        }
        case ScalaTokenTypes.tLPARENTHESIS | ScalaTokenTypes.tLINE_TERMINATOR | ScalaTokenTypes.tLBRACE => {
          if (state && ArgumentExprs.parse(builder)) {
            val tMarker = marker.precede
            marker.done(ScalaElementTypes.METHOD_CALL)
            subparse(tMarker)
          }
          else {
            marker.drop
          }
        }
        case ScalaTokenTypes.tLSQBRACKET => {
          state = true
          TypeArgs parse builder
          val tMarker = marker.precede
          marker.done(ScalaElementTypes.GENERIC_CALL)
          subparse(tMarker)
        }
        case _ => {
          marker.drop
        }
      }
    }
    subparse(newMarker)
    return true
  }
}