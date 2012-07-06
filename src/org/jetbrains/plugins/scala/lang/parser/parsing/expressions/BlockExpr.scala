package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package expressions

import lexer.ScalaTokenTypes
import patterns.CaseClauses
import builder.ScalaPsiBuilder
import parser.util.{ParserPatcher, ParserUtils}

/**
* @author Alexander Podkhalyuzin
* Date: 06.03.2008
*/

/*
 * BlockExpr ::= '{' CaseClauses '}'
 *             | '{' Block '}'
 */
object BlockExpr {
  def parse(builder: ScalaPsiBuilder): Boolean = {
    if (ParserPatcher.getSuitablePatcher(builder).parse(builder)) return true
    val blockExprMarker = builder.mark
    builder.getTokenType match {
      case ScalaTokenTypes.tLBRACE => {
        builder.advanceLexer()
        builder.enableNewlines
      }
      case _ => {
        blockExprMarker.drop()
        return false
      }
    }
    def foo() {
      builder.getTokenType match {
        case ScalaTokenTypes.kCASE => {
          val backMarker = builder.mark
          builder.advanceLexer()
          builder.getTokenType match {
            case ScalaTokenTypes.kCLASS |
                 ScalaTokenTypes.kOBJECT => {
               backMarker.rollbackTo()
              Block parse builder
            }
            case _ => {
              backMarker.rollbackTo()
              CaseClauses parse builder
            }
          }
        }
        case _ => {
          Block parse builder
        }
      }
    }
    ParserUtils.parseLoopUntilRBrace(builder, foo _)
    builder.restoreNewlinesState
    blockExprMarker.done(ScalaElementTypes.BLOCK_EXPR)
    true
  }
}