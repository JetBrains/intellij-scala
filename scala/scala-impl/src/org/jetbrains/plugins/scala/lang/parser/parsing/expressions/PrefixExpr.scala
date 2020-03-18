package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package expressions

import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.parsing.types.Path

/** 
* @author Alexander Podkhalyuzin
* Date: 03.03.2008
*/

/*
 * PrefixExpr ::= ['-' | '+' | '~' | '!'] SimpleExpr
 */
object PrefixExpr extends ParsingRule {
  override def apply()(implicit builder: ScalaPsiBuilder): Boolean = {
    builder.getTokenText match {
      case "-" | "+" | "~" | "!" =>
        val prefixMarker = builder.mark
        val refExpr = builder.mark
        builder.advanceLexer()
        refExpr.done(ScalaElementType.REFERENCE_EXPRESSION)
        if (!SimpleExpr()) {
          prefixMarker.rollbackTo()
          Path.parse(builder, ScalaElementType.REFERENCE_EXPRESSION)
        } else {
          prefixMarker.done(ScalaElementType.PREFIX_EXPR);
          true
        }
      case _ => SimpleExpr()
    }
  }
}