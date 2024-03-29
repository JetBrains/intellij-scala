package org.jetbrains.plugins.scala.lang.parser.parsing.expressions

import org.jetbrains.plugins.scala.lang.parser.ScCodeBlockElementType
import org.jetbrains.plugins.scala.lang.parser.parsing.ParsingRule
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.parsing.patterns.CaseClauses
import org.jetbrains.plugins.scala.lang.parser.util.ParserUtils

/*
 * BlockExpr ::= '{' CaseClauses '}'
 *             | '{' Block '}'
 */
object BlockExpr extends ParsingRule {

  import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenType._
  import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes._

  override def parse(implicit builder: ScalaPsiBuilder): Boolean = {
    if (builder.skipExternalToken()) return true

    val blockExprMarker = builder.mark()
    builder.getTokenType match {
      case `tLBRACE` =>
        builder.advanceLexer()
        builder.enableNewlines()
      case _ =>
        blockExprMarker.drop()
        return false
    }
    builder.withIndentationRegion(builder.newBracedIndentationRegionHere) {
      ParserUtils.parseLoopUntilRBrace() {
        builder.getTokenType match {
          case `kCASE` =>
            val backMarker = builder.mark()
            builder.advanceLexer()
            builder.getTokenType match {
              case ClassKeyword | ObjectKeyword =>
                backMarker.rollbackTo()
                Block.ContentInBraces()
              case _ =>
                backMarker.rollbackTo()
                CaseClauses()
            }
          case _ =>
            Block.ContentInBraces()
        }
      }
    }
    builder.restoreNewlinesState()
    blockExprMarker.done(ScCodeBlockElementType.BlockExpression)
    true
  }
}
