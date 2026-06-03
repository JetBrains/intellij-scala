package org.jetbrains.plugins.scala.lang.parser.parsing.expressions

import com.intellij.lang.PsiBuilder
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.lang.lexer.{ScalaTokenType, ScalaTokenTypes}
import org.jetbrains.plugins.scala.lang.parser.ScalaElementType
import org.jetbrains.plugins.scala.lang.parser.parsing.ParsingRule
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.parsing.params.TypeParamClause
import org.jetbrains.plugins.scala.lang.parser.util.InScala3

/*
 * Expr ::=  (Bindings | [‘implicit’] id | ‘_’) ‘=>’ Expr
 *        |  Expr1
 *
 * In Scala 3:
 * Expr              ::=  [`implicit'] FunParams ‘=>’ Expr
 *                     |  HkTypeParamClause ‘=>’ Block
 *                     |  Expr1
 *
 * implicit closures are actually implemented in other parts of the parser, not here! The grammar
 * from the Scala Reference does not match the implementation in Parsers.scala.
 */
object Expr extends ParsingRule {
  private def parseSimpleLambdaParamMarker()(implicit builder: ScalaPsiBuilder): Option[PsiBuilder.Marker] = {
    val paramMarker = builder.mark()

    val modifierMarker = builder.mark()
    val hasErasedModifier = builder.isScala3 &&
      builder.getTokenType == ScalaTokenTypes.tIDENTIFIER &&
      builder.lookAhead(1, ScalaTokenTypes.tIDENTIFIER) &&
      builder.tryParseSoftKeyword(ScalaTokenType.ErasedKeyword)
    if (hasErasedModifier) modifierMarker.done(ScalaElementType.MODIFIERS)
    else modifierMarker.drop()

    builder.getTokenType match {
      case ScalaTokenTypes.tIDENTIFIER | ScalaTokenTypes.tUNDER =>
        builder.advanceLexer() // ate id or _
        Some(paramMarker)
      case _ =>
        paramMarker.rollbackTo()
        None
    }
  }

  override def parse(implicit builder: ScalaPsiBuilder): Boolean = {
    val exprMarker = builder.mark()

    builder.getTokenType match {
      case ScalaTokenTypes.tIDENTIFIER | ScalaTokenTypes.tUNDER =>
        parseSimpleLambdaParamMarker() match {
          case Some(pmarker) =>
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
          case None =>
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
      case InScala3(ScalaTokenTypes.tLSQBRACKET) if TypeParamClause(
        mayHaveViewBounds    = false,
        mayHaveContextBounds = builder.features.`new context bounds and givens`
      ) =>
        builder.getTokenType match {
          case ScalaTokenTypes.tFUNTYPE =>
            builder.advanceLexer() // ate =>
            ExprInIndentationRegion()
            exprMarker.done(ScalaElementType.POLY_FUNCTION_EXPR)
            return true
          case _ =>
            builder.error(ScalaBundle.message("fun.sign.expected"))
            // Even though we couldn't even parse the =>,
            // we still create a PolyFunctionExpr.
            // We do that to give the parsed TypeParams a valid parent node
            // instead of discarding the TypeParams.
            exprMarker.done(ScalaElementType.POLY_FUNCTION_EXPR)
            return true
        }

      case _ => exprMarker.rollbackTo()
    }
    Expr1()
  }
}
