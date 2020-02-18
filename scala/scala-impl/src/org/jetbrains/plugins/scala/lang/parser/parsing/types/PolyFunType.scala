package org.jetbrains.plugins.scala.lang.parser.parsing.types

import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementType
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.parsing.params.TypeParamClause

/**
 * [[PolyFunType]] ::= [[TypeParamClause]] '=>' [[Type]]
 */
object PolyFunType {
  def parse(star: Boolean, isPattern: Boolean)(implicit builder: ScalaPsiBuilder): Boolean = {
    val marker = builder.mark()
    if (!TypeParamClause.parse(builder, mayHaveViewBounds = false, mayHaveContextBounds = false)) {
      marker.rollbackTo()
      false
    } else {
      builder.getTokenType match {
        case ScalaTokenTypes.tFUNTYPE =>
          builder.advanceLexer()
          if (!Type.parse(builder, star, isPattern)) builder.error(ScalaBundle.message("wrong.type"))
          marker.done(ScalaElementType.POLY_FUNCTION_TYPE)
          true
        case _ =>
          marker.rollbackTo()
          false
      }
    }
  }
}
