package org.jetbrains.plugins.scala.lang.parser.parsing.expressions

import com.intellij.psi.tree.IElementType
import org.jetbrains.annotations.{Nls, Nullable}
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.lang.parser.parsing.CommonUtils.remapRawStringTokens
import org.jetbrains.plugins.scala.lang.parser.parsing.{CommonUtils, ParsingRule}
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder

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
 */
object Literal extends ParsingRule {

  import org.jetbrains.plugins.scala.lang.parser.ScalaElementType._
  import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenType._
  import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes._

  override def parse(implicit builder: ScalaPsiBuilder): Boolean = {
    val marker = builder.mark()

    def advanceAndMarkDone(elementType: ScExpressionElementType,
                           @Nullable @Nls errorMessage: String = null): Boolean = {
      builder.advanceLexer()
      if (errorMessage != null) builder.error(errorMessage)
      marker.done(elementType)
      true
    }

    def matchNumber(@Nullable tokenType: IElementType) = tokenType match {
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
        CommonUtils.parseInterpolatedString(isPattern = false)
        marker.done(InterpolatedString)
        true
      case `tINTERPOLATED_MULTILINE_STRING` |
           `tINTERPOLATED_STRING` |
           `tINTERPOLATED_MULTILINE_RAW_STRING` |
           `tINTERPOLATED_RAW_STRING` =>
        // TODO: is this dead branch? it's not triggered by tests
        remapRawStringTokens(builder)
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
      //case `tWRONG_LINE_BREAK_IN_STRING` => TODO: it's currently not added in non-interpolated string literals (see TODOs in scala.flex)
      case tokenType =>
        matchNumber(tokenType)
    }
  }
}