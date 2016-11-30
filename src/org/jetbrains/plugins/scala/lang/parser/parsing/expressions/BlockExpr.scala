package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package expressions

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.parsing.patterns.CaseClauses
import org.jetbrains.plugins.scala.lang.parser.util.{ParserPatcher, ParserUtils}

/**
* @author Alexander Podkhalyuzin
* Date: 06.03.2008
*/

/*
 * BlockExpr ::= '{' CaseClauses '}'
 *             | '{' Block '}'
 */
object BlockExpr extends BlockExpr {
  override protected def block = Block
  override protected def caseClauses = CaseClauses
}

trait BlockExpr {
  protected def block: Block
  protected def caseClauses: CaseClauses

  def parse(builder: ScalaPsiBuilder): Boolean = {
    if (ParserPatcher.getSuitablePatcher(builder).parse(builder)) return true
    val blockExprMarker = builder.mark
    builder.getTokenType match {
      case ScalaTokenTypes.tLBRACE =>
        builder.advanceLexer()
        builder.enableNewlines
      case _ =>
        blockExprMarker.drop()
        return false
    }
    def loopFunction() {
      builder.getTokenType match {
        case ScalaTokenTypes.kCASE =>
          val backMarker = builder.mark
          builder.advanceLexer()
          builder.getTokenType match {
            case ScalaTokenTypes.kCLASS |
                 ScalaTokenTypes.kOBJECT =>
              backMarker.rollbackTo()
              block.parse(builder)
            case _ =>
              backMarker.rollbackTo()
              caseClauses parse builder
          }
        case _ =>
          block.parse(builder)
      }
    }
    ParserUtils.parseLoopUntilRBrace(builder, loopFunction)
    builder.restoreNewlinesState
    blockExprMarker.done(ScalaElementTypes.BLOCK_EXPR)
    true
  }
}