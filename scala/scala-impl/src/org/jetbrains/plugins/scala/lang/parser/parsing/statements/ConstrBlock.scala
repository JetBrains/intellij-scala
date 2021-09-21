package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package statements

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.parsing.expressions.{Block, BlockStat, SelfInvocation}
import org.jetbrains.plugins.scala.lang.parser.util.ParserUtils

object ConstrBlock extends ParsingRule {
  object BlockContentAfterSelfInvocation extends Block.ContentInBraces {
    override def parseStmt()(implicit builder: ScalaPsiBuilder): Boolean = BlockStat()
  }

  override def parse(implicit builder: ScalaPsiBuilder): Boolean = {
    val constrExprMarker = builder.mark()
    builder.getTokenType match {
      case ScalaTokenTypes.tLBRACE =>
        builder.advanceLexer() //Ate {
        builder.enableNewlines()
        SelfInvocation()
        ParserUtils.parseLoopUntilRBrace() {
          BlockContentAfterSelfInvocation()
        }
        constrExprMarker.done(ScalaElementType.CONSTR_BLOCK_EXPR)
        true
      case _ =>
        constrExprMarker.drop()
        false
    }
  }
}