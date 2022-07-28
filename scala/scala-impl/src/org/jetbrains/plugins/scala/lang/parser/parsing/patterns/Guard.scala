package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package patterns

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.parsing.expressions.PostfixExprInIndentationRegion

object Guard {
  def apply(noIf: Boolean = false)(implicit builder: ScalaPsiBuilder): Boolean = {
    val guardMarker = builder.mark()
    builder.getTokenType match {
      case ScalaTokenTypes.kIF =>
        builder.advanceLexer() //Ate if
      case _ =>
        if (!noIf) {
          guardMarker.drop()
          return false
        }
    }
    // todo: handle indention
    if (!PostfixExprInIndentationRegion()) {
      if (noIf) {
        guardMarker.drop()
        return false
      }
      builder error ErrMsg("wrong.postfix.expression")
    }
    guardMarker.done(ScalaElementType.GUARD)
    true
  }
}