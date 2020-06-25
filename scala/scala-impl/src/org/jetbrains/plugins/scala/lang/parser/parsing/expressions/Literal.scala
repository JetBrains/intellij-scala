package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package expressions

import com.intellij.psi.tree.IElementType
import org.jetbrains.annotations.{Nls, Nullable}

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
  import lang.lexer._
  import ScalaTokenType._
  import ScalaTokenTypes._

  def parse(implicit builder: ScalaPsiBuilder): Boolean = {
    val marker = builder.mark()

    def advanceAndMarkDone(elementType: ScExpressionElementType,
                           @Nullable @Nls errorMessage: String = null) = {
      builder.advanceLexer()
      if (errorMessage != null) builder.error(errorMessage)
      marker.done(elementType)
      true
    }

    def matchNumber(tokenType: IElementType) = tokenType match {
      case Long =>
        advanceAndMarkDone(LongLiteral)
      case Integer =>
        advanceAndMarkDone(IntegerLiteral)
      case Double =>
        advanceAndMarkDone(DoubleLiteral)
      case Float =>
        advanceAndMarkDone(FloatLiteral)
      case _ =>
        marker.rollbackTo()
        false
    }

    builder.getTokenType match {
      case `tIDENTIFIER` if builder.getTokenText == "-" =>
        builder.advanceLexer() //Ate -
        matchNumber(builder.getTokenType)
      case `tINTERPOLATED_STRING_ID` =>
        CommonUtils.parseInterpolatedString(builder, isPattern = false)
        marker.done(InterpolatedString)
        true
      case `tINTERPOLATED_MULTILINE_STRING` |
           `tINTERPOLATED_STRING` =>
        advanceAndMarkDone(InterpolatedString)
      case `kNULL` =>
        advanceAndMarkDone(NullLiteral)
      case `kTRUE` |
           `kFALSE` =>
        advanceAndMarkDone(BooleanLiteral)
      case `tSYMBOL` =>
        advanceAndMarkDone(SymbolLiteral)
      case `tCHAR` =>
        advanceAndMarkDone(CharLiteral)
      case `tSTRING` |
           `tMULTILINE_STRING` =>
        advanceAndMarkDone(StringLiteral)
      case `tWRONG_STRING` =>
        advanceAndMarkDone(StringLiteral, ScalaBundle.message("wrong.string.literal"))
      case tokenType =>
        matchNumber(tokenType)
    }
  }
}