package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package statements

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.parsing.expressions.{BlockStat, SelfInvocation}

/**
* @author Alexander Podkhalyuzin
* Date: 13.03.2008
*/
object ConstrBlock extends ConstrBlock {
  override protected val selfInvocation = SelfInvocation
  override protected val blockStat = BlockStat
}

trait ConstrBlock {
  protected val selfInvocation: SelfInvocation
  protected val blockStat: BlockStat

  def parse(builder: ScalaPsiBuilder): Boolean = {
    val constrExprMarker = builder.mark
    builder.getTokenType match {
      case ScalaTokenTypes.tLBRACE =>
        builder.advanceLexer() //Ate {
        builder.enableNewlines
        selfInvocation parse builder
        while (true) {
          builder.getTokenType match {
            case ScalaTokenTypes.tRBRACE => {
              builder.advanceLexer() //Ate }
              builder.restoreNewlinesState
              constrExprMarker.done(ScalaElementTypes.CONSTR_BLOCK)
              return true
            }
            case ScalaTokenTypes.tSEMICOLON => {
              builder.advanceLexer() //Ate semi
              blockStat parse builder
            }
            case _ if builder.newlineBeforeCurrentToken =>
              if (!blockStat.parse(builder)) {
                builder error ErrMsg("rbrace.expected")
                builder.restoreNewlinesState
                while (!builder.eof && !ScalaTokenTypes.tRBRACE.eq(builder.getTokenType) &&
                  !builder.newlineBeforeCurrentToken) {
                  builder.advanceLexer()
                }
                constrExprMarker.done(ScalaElementTypes.CONSTR_BLOCK)
                return true
              }
            case _ => {
              builder error ErrMsg("rbrace.expected")
              builder.restoreNewlinesState
              while (!builder.eof && !ScalaTokenTypes.tRBRACE.eq(builder.getTokenType) &&
                !builder.newlineBeforeCurrentToken) {
                builder.advanceLexer()
              }
              constrExprMarker.done(ScalaElementTypes.CONSTR_BLOCK)
              return true
            }
          }
        }
        true //it's trick to compiler
      case _ =>
        constrExprMarker.drop()
        false
    }
  }
}