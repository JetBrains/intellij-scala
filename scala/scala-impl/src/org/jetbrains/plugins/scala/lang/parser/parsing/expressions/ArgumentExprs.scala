package org.jetbrains.plugins.scala.lang.parser.parsing.expressions

import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.lang.lexer.{ScalaTokenType, ScalaTokenTypes}
import org.jetbrains.plugins.scala.lang.parser.ScalaElementType
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
    if (!canContinueWithArgumentExprs) {
      return false
    }
    val argMarker = builder.mark()
    builder.getTokenType match {
      case ScalaTokenTypes.tLPARENTHESIS =>
        builder.advanceLexer() //Ate (
        builder.disableNewlines()

        // In Scala 2 "using" must be followed by an identifier
        def isUsingInScala2 =
          builder.features.usingInArgumentsEnabled &&
          builder.getTokenText == "using" &&
          ScalaTokenTypes.EXPR_START_TOKEN_SET.contains(builder.lookAhead(1))

        if (builder.isScala3 || isUsingInScala2) {
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
        BlockExpr()
        argMarker.done(ScalaElementType.ARG_EXPRS)
        true
      case _ =>
        argMarker.drop()
        false
    }
  }

  def canContinueWithArgumentExprs(implicit builder: ScalaPsiBuilder): Boolean = {
    if (builder.isScala3IndentationBasedSyntaxEnabled) {
      !builder.newlineBeforeCurrentToken ||
        builder.isIndentHere && !builder.twoNewlinesBeforeCurrentToken
    } else {
      builder.getTokenType == ScalaTokenTypes.tLBRACE && !builder.twoNewlinesBeforeCurrentToken ||
        !builder.newlineBeforeCurrentToken
    }
  }
}