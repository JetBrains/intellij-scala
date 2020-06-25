package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package expressions

import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.parsing.patterns.CaseClauses
import org.jetbrains.plugins.scala.lang.parser.util.ParserUtils

/**
* @author Alexander Podkhalyuzin
* Date: 06.03.2008
*/

/*
 * BlockExpr ::= '{' CaseClauses '}'
 *             | '{' Block '}'
 */
object BlockExpr extends ParsingRule {

  import lexer.ScalaTokenType._
  import lexer.ScalaTokenTypes._

  override def apply()(implicit builder: ScalaPsiBuilder): Boolean = {
    if (builder.skipExternalToken()) return true

    val blockExprMarker = builder.mark
    builder.getTokenType match {
      case `tLBRACE` =>
        builder.advanceLexer()
        builder.enableNewlines()
      case _ =>
        blockExprMarker.drop()
        return false
    }
    val blockIndentation = BlockIndentation.create
    def loopFunction(): Unit = {
      builder.getTokenType match {
        case `kCASE` =>
          val backMarker = builder.mark
          builder.advanceLexer()
          builder.getTokenType match {
            case ClassKeyword | ObjectKeyword =>
              backMarker.rollbackTo()
              Block.parse(builder)
            case _ =>
              backMarker.rollbackTo()
              CaseClauses parse builder
          }
        case _ =>
          blockIndentation.fromHere()
          Block.parse(builder)
      }
    }
    ParserUtils.parseLoopUntilRBrace(builder, loopFunction _)
    blockIndentation.drop()
    builder.restoreNewlinesState()
    blockExprMarker.done(ScCodeBlockElementType.BlockExpression)
    true
  }
}