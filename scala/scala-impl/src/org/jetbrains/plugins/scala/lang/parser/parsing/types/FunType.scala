package org.jetbrains.plugins.scala.lang.parser.parsing.types

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenType
import org.jetbrains.plugins.scala.lang.parser.ScalaElementType
import org.jetbrains.plugins.scala.lang.parser.parsing.ParsingRule
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder

/**
 * [[FunType]] ::= ['given'] ([[MonoFunType]] | [[PolyFunType]])
 */
object FunType extends ParsingRule {
  override def parse()(implicit builder: ScalaPsiBuilder): Boolean = {
    val marker = builder.mark()

    builder.getTokenText match {
      case ScalaTokenType.Given.debugName =>
        builder.remapCurrentToken(ScalaTokenType.Given)
        builder.advanceLexer()
      case _ => ()
    }

    if (MonoFunType.parse()) {
//      marker.done(ScalaElementType.)
      ???
    } else if (PolyFunType.parse()) {
      marker.done(ScalaElementType.POLY_FUNCTION_TYPE)
      true
    } else {
      marker.rollbackTo()
      false
    }
  }
}
