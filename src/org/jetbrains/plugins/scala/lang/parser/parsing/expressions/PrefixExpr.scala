package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package expressions

import com.intellij.lang.PsiBuilder
import builder.ScalaPsiBuilder

/** 
* @author Alexander Podkhalyuzin
* Date: 03.03.2008
*/

/*
 * PrefixExpr ::= ['-' | '+' | '~' | '!'] SimpleExpr
 */

object PrefixExpr {
  def parse(builder: ScalaPsiBuilder): Boolean = {
    builder.getTokenText match {
      case "-" | "+" | "~" | "!" =>
        val prefixMarker = builder.mark
        val refExpr = builder.mark
        builder.advanceLexer()
        refExpr.done(ScalaElementTypes.REFERENCE_EXPRESSION)
        if (!SimpleExpr.parse(builder)) {
          prefixMarker.rollbackTo(); false
        } else {
          prefixMarker.done(ScalaElementTypes.PREFIX_EXPR); true
        }
      case _ => SimpleExpr.parse(builder)
    }
  }
}