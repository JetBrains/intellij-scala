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
    val marker = builder.mark()

    builder.getTokenType match {
      case `tIDENTIFIER` if builder.getTokenText == "-" =>
        builder.advanceLexer() //Ate -
        builder.getTokenType match {
          case `tINTEGER` |
               `tFLOAT` =>
            advanceAndMarkDone(marker)(LITERAL)
          case _ =>
            marker.rollbackTo()
            false
        }
      case `tINTERPOLATED_STRING_ID` =>
        CommonUtils.parseInterpolatedString(builder, isPattern = false)
        marker.done(INTERPOLATED_STRING_LITERAL)
        true
      case `tINTERPOLATED_MULTILINE_STRING` |
           `tINTERPOLATED_STRING` =>
        advanceAndMarkDone(marker)(INTERPOLATED_STRING_LITERAL)
      case `tINTEGER` |
           `tFLOAT` |
           `kTRUE` |
           `kFALSE` |
           `tCHAR` |
           `tSYMBOL` |
           `kNULL` |
           `tSTRING` |
           `tMULTILINE_STRING` =>
        advanceAndMarkDone(marker)(LITERAL)
      case `tWRONG_STRING` =>
        advanceAndMarkDone(marker, "Wrong string literal")(LITERAL)
      case _ =>
        marker.rollbackTo()
        false
    }
  }

  private def advanceAndMarkDone(marker: Marker, errorMessage: String = null)
                                (elementType: ScExpressionElementType)
                                (implicit builder: ScalaPsiBuilder) = {
    builder.advanceLexer()
    if (errorMessage != null) builder.error(errorMessage)
    marker.done(elementType)
    true
  }
}