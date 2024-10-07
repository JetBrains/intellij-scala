package org.jetbrains.plugins.scala.lang.parser.parsing.expressions

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementType
import org.jetbrains.plugins.scala.lang.parser.parsing.ParsingRule
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.parsing.types.Path

/*
 * PrefixExpr ::= ['-' | '+' | '~' | '!'] SimpleExpr
 */
object PrefixExpr extends ParsingRule {
  override def parse(implicit builder: ScalaPsiBuilder): Boolean = {
    builder.getTokenText match {
      case "-" if ScalaTokenTypes.NUMBER_TOKEN_SET.contains(builder.lookAhead(1)) =>

        SimpleExpr()
      case "-" | "+" | "~" | "!" =>
        val prefixMarker = builder.mark()
        val refExpr = builder.mark()
        builder.advanceLexer()
        refExpr.done(ScalaElementType.REFERENCE_EXPRESSION)
        if (!SimpleExpr()) {
          prefixMarker.rollbackTo()
          Path(ScalaElementType.REFERENCE_EXPRESSION)
        } else {
          prefixMarker.done(ScalaElementType.PREFIX_EXPR);
          true
        }
      case _ => SimpleExpr()
    }
  }
}