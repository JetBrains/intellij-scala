package org.jetbrains.plugins.scala.lang.parser.parsing.expressions

import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.lang.lexer.{ScalaTokenType, ScalaTokenTypes}
import org.jetbrains.plugins.scala.lang.parser.{ErrMsg, ScalaElementType}
import org.jetbrains.plugins.scala.lang.parser.parsing.ParsingRule
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder

/**
 * Scala 2: {{{
 * ArgumentExprs     ::=  ‘(’ [Exprs] ‘)’
 *                     |  ‘(’ [Exprs ‘,’] PostfixExpr ‘:’ ‘_’ ‘*’ ‘)’
 *                     |  [nl] BlockExpr
 * }}}
 *
 * Scala 3: {{{
 * ArgumentExprs     ::=  ParArgumentExprs
 *                     |  BlockExpr
 * ParArgumentExprs  ::=  ‘(’ [‘using’] ExprsInParens ‘)’
 *                     |  ‘(’ [ExprsInParens ‘,’] PostfixExpr ‘*’ ‘)’
 * }}}
 */
object ArgumentExprs extends ParsingRule {

  override def parse(implicit builder: ScalaPsiBuilder): Boolean = {
    val argMarker = builder.mark()
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
          if (!Expr()) builder.wrongExpressionError()
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
        val blockCantBeArgs = builder.twoNewlinesBeforeCurrentToken || builder.isScala3 && builder.newlineBeforeCurrentToken
        if (blockCantBeArgs) {
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