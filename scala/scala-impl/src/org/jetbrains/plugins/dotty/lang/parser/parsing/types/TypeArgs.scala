package org.jetbrains.plugins.dotty.lang.parser.parsing.types

import org.jetbrains.plugins.dotty.lang.parser.DottyElementTypes.TYPE_ARGUMENT_NAME
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder

/**
  * @author adkozlov
  */

/*
 * TypeArgs ::= `[' ArgTypes | NamedArgTypes `]'
 */
object TypeArgs extends org.jetbrains.plugins.scala.lang.parser.parsing.types.TypeArgs {
  override protected def parseComponent(builder: ScalaPsiBuilder) = {
    val marker = builder.mark()
    builder.getTokenType match {
      case ScalaTokenTypes.tIDENTIFIER =>
        builder.advanceLexer()
        builder.getTokenType match {
          case ScalaTokenTypes.tASSIGN =>
            marker.done(TYPE_ARGUMENT_NAME)
            builder.advanceLexer()
          case _ => marker.rollbackTo()
        }
      case _ => marker.drop()
    }
    Type.parse(builder)
  }
}
