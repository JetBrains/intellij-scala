package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package expressions

import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder

/** 
* @author Alexander Podkhalyuzin
* Date: 03.03.2008
*/

/*
 * PrefixExpr ::= ['-' | '+' | '~' | '!'] SimpleExpr
 */
object PrefixExpr extends PrefixExpr {
  override protected val simpleExpr = SimpleExpr
}

trait PrefixExpr {
  protected val simpleExpr: SimpleExpr

  def parse(builder: ScalaPsiBuilder): Boolean = {
    builder.getTokenText match {
      case "-" | "+" | "~" | "!" =>
        val prefixMarker = builder.mark
        val refExpr = builder.mark
        builder.advanceLexer()
        refExpr.done(ScalaElementTypes.REFERENCE_EXPRESSION)
        if (!simpleExpr.parse(builder)) {
          prefixMarker.rollbackTo(); false
        } else {
          prefixMarker.done(ScalaElementTypes.PREFIX_EXPR); true
        }
      case _ => simpleExpr.parse(builder)
    }
  }
}