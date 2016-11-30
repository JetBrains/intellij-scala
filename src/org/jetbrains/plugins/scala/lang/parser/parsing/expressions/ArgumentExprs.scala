package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package expressions

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder

/**
* @author Alexander Podkhalyuzin
* Date: 06.03.2008
*/

/*
 * ArgumentExprs ::= '(' [Exprs [',']] ')'
 *                 | [nl] BlockExpr
 */
object ArgumentExprs extends ArgumentExprs {
  override protected def blockExpr = BlockExpr
  override protected def expr = Expr
}

trait ArgumentExprs {
  protected def blockExpr: BlockExpr
  protected def expr: Expr

  def parse(builder: ScalaPsiBuilder): Boolean = {
    val argMarker = builder.mark
    builder.getTokenType match {
      case ScalaTokenTypes.tLPARENTHESIS =>
        builder.advanceLexer() //Ate (
        builder.disableNewlines
        expr parse builder
        while (builder.getTokenType == ScalaTokenTypes.tCOMMA) {
          builder.advanceLexer()
          if (!expr.parse(builder)) {
            builder error ErrMsg("wrong.expression")
          }
        }
        builder.getTokenType match {
          case ScalaTokenTypes.tRPARENTHESIS =>
            builder.advanceLexer() //Ate )
          case _ =>
            builder error ScalaBundle.message("rparenthesis.expected")
        }
        builder.restoreNewlinesState
        argMarker.done(ScalaElementTypes.ARG_EXPRS)
        true
      case ScalaTokenTypes.tLBRACE =>
        if (builder.twoNewlinesBeforeCurrentToken) {
          argMarker.rollbackTo()
          return false
        }
        blockExpr parse builder
        argMarker.done(ScalaElementTypes.ARG_EXPRS)
        true
      case _ =>
        argMarker.drop()
        false
    }
  }
}