package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package expressions

import org.jetbrains.plugins.scala.lang.lexer.{ScalaTokenType, ScalaTokenTypes}
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder

/**
* @author Alexander Podkhalyuzin
* Date: 06.03.2008
*/

/*
 * ArgumentExprs ::= '(' [Exprs [',']] ')'
 *                 | [nl] BlockExpr
 */
object ArgumentExprs extends ParsingRule {

  override def apply()(implicit builder: ScalaPsiBuilder): Boolean = {
    val argMarker = builder.mark
    builder.getTokenType match {
      case ScalaTokenTypes.tLPARENTHESIS =>
        builder.advanceLexer() //Ate (
        builder.disableNewlines()

        if (builder.isScala3) {
          builder.tryParseSoftKeyword(ScalaTokenType.UsingKeyword)
        }

        Expr()
        while (builder.getTokenType == ScalaTokenTypes.tCOMMA && !builder.consumeTrailingComma(ScalaTokenTypes.tRPARENTHESIS)) {
          builder.advanceLexer()
          if (!Expr()) builder error ErrMsg("wrong.expression")
        }
      
        builder.getTokenType match {
          case ScalaTokenTypes.tRPARENTHESIS =>
            builder.advanceLexer() //Ate )
          case _ =>
            builder error ScalaBundle.message("rparenthesis.expected")
        }
        builder.restoreNewlinesState()
        argMarker.done(ScalaElementType.ARG_EXPRS)
        true
      case ScalaTokenTypes.tLBRACE =>
        if (builder.twoNewlinesBeforeCurrentToken) {
          argMarker.rollbackTo()
          return false
        }
        BlockExpr()
        argMarker.done(ScalaElementType.ARG_EXPRS)
        true
      case _ =>
        argMarker.drop()
        false
    }
  }
}