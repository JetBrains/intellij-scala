package org.jetbrains.plugins.scala.lang.parser.parsing.expressions

import org.jetbrains.plugins.scala.lang.lexer.{ScalaTokenType, ScalaTokenTypes}
import org.jetbrains.plugins.scala.lang.parser.{ErrMsg, ScalaElementType}
import org.jetbrains.plugins.scala.lang.parser.parsing.ParsingRule
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder

/*
 * Expr ::= (Bindings | [‘implicit’] id | ‘_’) ‘=>’ Expr
 *         | Expr1
 *
 * In Scala 3:
 * Expr              ::= [`implicit'] FunParams ‘=>’ Expr
 *                     | Expr1
 *
 * implicit closures are actually implemented in other parts of the parser, not here! The grammar
 * from the Scala Reference does not match the implementation in Parsers.scala.
 */
object Expr extends ParsingRule {
  override def parse(implicit builder: ScalaPsiBuilder): Boolean = {
    val exprMarker = builder.mark()

    builder.getTokenType match {
      case ScalaTokenTypes.tIDENTIFIER | ScalaTokenTypes.tUNDER =>
        val pmarker = builder.mark()
        builder.advanceLexer() //Ate id
        builder.getTokenType match {
          case ScalaTokenTypes.tFUNTYPE | ScalaTokenType.ImplicitFunctionArrow =>
            completeParamClauses(pmarker)()

            builder.advanceLexer() //Ate =>
            if (!ExprInIndentationRegion()) builder.wrongExpressionError()
            exprMarker.done(ScalaElementType.FUNCTION_EXPR)
            return true
          case _ =>
            pmarker.drop()
            exprMarker.rollbackTo()
        }

      case ScalaTokenTypes.tLPARENTHESIS =>
        if (builder.getTokenType == ScalaTokenTypes.kIMPLICIT) {
          builder.advanceLexer() // ate implicit
        }
        if (Bindings()) {
          builder.getTokenType match {
            case ScalaTokenTypes.tFUNTYPE | ScalaTokenType.ImplicitFunctionArrow =>
              builder.advanceLexer() //Ate =>
              if (!ExprInIndentationRegion()) builder.wrongExpressionError()
              exprMarker.done(ScalaElementType.FUNCTION_EXPR)
              return true
            case _ => exprMarker.rollbackTo()
          }
        }
        else {
          exprMarker.drop()
        }
      case _ => exprMarker.rollbackTo()
    }
    Expr1()
  }
}
