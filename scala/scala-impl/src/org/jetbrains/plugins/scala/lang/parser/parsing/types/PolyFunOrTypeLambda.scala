package org.jetbrains.plugins.scala.lang.parser.parsing.types

import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.lang.lexer.{ScalaTokenType, ScalaTokenTypes}
import org.jetbrains.plugins.scala.lang.parser.ScalaElementType
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.parsing.params.TypeParamClause

/**
 * [[PolyFunOrTypeLambda]] ::= [[TypeParamClause]] ('=>' [[Type]] | '=>>' [[Type]] )
 */
object PolyFunOrTypeLambda {
  def apply(star: Boolean, isPattern: Boolean)(implicit builder: ScalaPsiBuilder): Boolean = {
    if (!builder.isScala3) false
    else {
      val marker = builder.mark()
      if (!TypeParamClause(
        mayHaveViewBounds    = false,
        mayHaveContextBounds = builder.features.`new context bounds and givens`,
        mayHaveVariance      = false
      )) {
        marker.rollbackTo()
        false
      } else {
        builder.getTokenType match {
          case ScalaTokenTypes.tFUNTYPE =>
            builder.advanceLexer()
            if (!Type(star, isPattern)) builder.error(ScalaBundle.message("wrong.type"))
            marker.done(ScalaElementType.POLY_FUNCTION_TYPE)
            true
          case ScalaTokenType.TypeLambdaArrow =>
            builder.advanceLexer()
            if (!Type(star = star, isPattern = isPattern)) {
              builder.error(ScalaBundle.message("wrong.type"))
            }
            marker.done(ScalaElementType.TYPE_LAMBDA)
            true
          case _ =>
            marker.error(ScalaBundle.message("type.lambda.expected"))
            true
        }
      }
    }
  }
}
