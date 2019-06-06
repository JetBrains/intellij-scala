package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package expressions

import com.intellij.lang.PsiBuilder.Marker

/**
 * Literal ::= ['-']integerLiteral
 * | ['-']floatingPointLiteral
 * | booleanLiteral
 * | characterLiteral
 * | stringLiteral
 * | symbolLiteral
 * | true
 * | false
 * | null
 * | javaId"StringLiteral"
 *
 * @author Alexander Podkhalyuzin
 *         Date: 15.02.2008
 */
object Literal {

  import ScalaElementType._
  import builder.ScalaPsiBuilder
  import lang.lexer.ScalaTokenTypes._

  def parse(implicit builder: ScalaPsiBuilder): Boolean = {
    implicit val marker: Marker = builder.mark()

    builder.getTokenType match {
      case `tIDENTIFIER` if builder.getTokenText == "-" =>
        builder.advanceLexer() //Ate -
        builder.getTokenType match {
          case `tINTEGER` |
               `tFLOAT` =>
            advanceAndMarkDone(LiteralElementType)
          case _ =>
            marker.rollbackTo()
            false
        }
      case `tINTERPOLATED_STRING_ID` =>
        CommonUtils.parseInterpolatedString(builder, isPattern = false)
        marker.done(InterpolatedStringLiteralElementType)
        true
      case `tINTERPOLATED_MULTILINE_STRING` |
           `tINTERPOLATED_STRING` =>
        advanceAndMarkDone(InterpolatedStringLiteralElementType)
      case `kNULL` =>
        advanceAndMarkDone(NullLiteralElementType)
      case `kTRUE` |
           `kFALSE` =>
        advanceAndMarkDone(BooleanLiteralElementType)
      case `tSYMBOL` =>
        advanceAndMarkDone(SymbolLiteralElementType)
      case `tCHAR` =>
        advanceAndMarkDone(CharLiteralElementType)
      case `tINTEGER` |
           `tFLOAT` |
           `tSTRING` |
           `tMULTILINE_STRING` =>
        advanceAndMarkDone(LiteralElementType)
      case `tWRONG_STRING` =>
        advanceAndMarkDone(LiteralElementType, "Wrong string literal")
      case _ =>
        marker.rollbackTo()
        false
    }
  }

  private def advanceAndMarkDone(elementType: ScExpressionElementType,
                                 errorMessage: String = null)
                                (implicit marker: Marker,
                                 builder: ScalaPsiBuilder) = {
    builder.advanceLexer()
    if (errorMessage != null) builder.error(errorMessage)
    marker.done(elementType)
    true
  }
}