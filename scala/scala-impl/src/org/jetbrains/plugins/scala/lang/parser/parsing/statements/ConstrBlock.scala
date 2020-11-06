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
object ConstrBlock extends ParsingRule {

  override def apply()(implicit builder: ScalaPsiBuilder): Boolean = {
    val constrExprMarker = builder.mark
    builder.getTokenType match {
      case ScalaTokenTypes.tLBRACE =>
        builder.advanceLexer() //Ate {
        builder.enableNewlines()
        SelfInvocation()
        while (true) {
          builder.getTokenType match {
            case ScalaTokenTypes.tRBRACE => {
              builder.advanceLexer() //Ate }
              builder.restoreNewlinesState()
              constrExprMarker.done(ScalaElementType.CONSTR_BLOCK)
              return true
            }
            case ScalaTokenTypes.tSEMICOLON => {
              builder.advanceLexer() //Ate semi
              BlockStat()
            }
            case _ if builder.newlineBeforeCurrentToken =>
              if (!BlockStat()) {
                builder error ErrMsg("rbrace.expected")
                builder.restoreNewlinesState()
                while (!builder.eof && builder.getTokenType != ScalaTokenTypes.tRBRACE &&
                  !builder.newlineBeforeCurrentToken) {
                  builder.advanceLexer()
                }
                constrExprMarker.done(ScalaElementType.CONSTR_BLOCK)
                return true
              }
            case _ => {
              builder error ErrMsg("rbrace.expected")
              builder.restoreNewlinesState()
              while (!builder.eof && builder.getTokenType != ScalaTokenTypes.tRBRACE &&
                !builder.newlineBeforeCurrentToken) {
                builder.advanceLexer()
              }
              constrExprMarker.done(ScalaElementType.CONSTR_BLOCK)
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