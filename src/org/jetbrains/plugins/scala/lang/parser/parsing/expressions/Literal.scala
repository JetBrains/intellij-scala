package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package expressions

import lexer.{ScalaElementType, ScalaTokenTypes}
import builder.ScalaPsiBuilder
import parser.ScalaPsiCreator.SelfPsiCreator
import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.tree.IElementType
import psi.impl.expr.{ScInterpolatedStringPrefixReference, ScReferenceExpressionImpl}

/**
* @author Alexander Podkhalyuzin
* Date: 15.02.2008
*/

/*
Literal ::= ['-']integerLiteral
            | ['-']floatingPointLiteral
            | booleanLiteral
            | characterLiteral
            | stringLiteral
            | symbolLiteral
            | true
            | false
            | null
*/
object Literal {
  def parse(builder: ScalaPsiBuilder): Boolean = {
    val marker = builder.mark()
    builder.getTokenType match {
      case ScalaTokenTypes.tIDENTIFIER => {
        if (builder.getTokenText == "-") {
          builder.advanceLexer() //Ate -
          builder.getTokenType match {
            case ScalaTokenTypes.tINTEGER |
                 ScalaTokenTypes.tFLOAT => {
              builder.advanceLexer() //Ate literal
              marker.done(ScalaElementTypes.LITERAL)
              true
            }
            case _ => {
              marker.rollbackTo()
              false
            }
          }
        }
        else {
          marker.rollbackTo()
          false
        }
      }
      case ScalaTokenTypes.tINTERPOLATED_STRING_ID =>
        val prefixMarker = builder.mark()
        builder.advanceLexer()
        prefixMarker.done(new ScalaElementType("Interpolated String Prefix Reference") with SelfPsiCreator {
          def createElement(node: ASTNode): PsiElement = new ScInterpolatedStringPrefixReference(node)
        })
        while (!builder.eof() && builder.getTokenType != ScalaTokenTypes.tINTERPOLATED_STRING_END){
          if (builder.getTokenType == ScalaTokenTypes.tINTERPOLATED_STRING_INJECTION) {
            builder.advanceLexer()
            if (!BlockExpr.parse(builder)) {
              if (builder.getTokenType == ScalaTokenTypes.tIDENTIFIER) {
                val idMarker = builder.mark()
                builder.advanceLexer()
                idMarker.done(ScalaElementTypes.REFERENCE_EXPRESSION)
              } else {
                if (!builder.getTokenText.startsWith("$")) builder.error("Bad interpolated string injection")
              }
            }
          } else {
            if (builder.getTokenType == ScalaTokenTypes.tWRONG_STRING) {
              builder.error("Wrong string literal")
            }
            builder.advanceLexer()
          }
        }
        if (!builder.eof()) builder.advanceLexer()
        marker.done(ScalaElementTypes.INTERPOLATED_STRING_LITERAL)
        true
      case ScalaTokenTypes.tINTERPOLATED_MULTILINE_STRING | ScalaTokenTypes.tINTERPOLATED_STRING =>
        builder.advanceLexer()
        marker.done(ScalaElementTypes.INTERPOLATED_STRING_LITERAL)
        true
      case ScalaTokenTypes.tINTEGER | ScalaTokenTypes.tFLOAT |
           ScalaTokenTypes.kTRUE | ScalaTokenTypes.kFALSE |
           ScalaTokenTypes.tCHAR | ScalaTokenTypes.tSYMBOL |
           ScalaTokenTypes.kNULL | ScalaTokenTypes.tSTRING |
           ScalaTokenTypes.tMULTILINE_STRING  => {
        builder.advanceLexer() //Ate literal
        marker.done(ScalaElementTypes.LITERAL)
        true
      }
      case ScalaTokenTypes.tWRONG_STRING => {
        //wrong string literal
        builder.advanceLexer() //Ate wrong string
        builder.error("Wrong string literal")
        marker.done(ScalaElementTypes.LITERAL)
        true
      }
      case _ => {
        marker.rollbackTo()
        false
      }
    }
  }
}