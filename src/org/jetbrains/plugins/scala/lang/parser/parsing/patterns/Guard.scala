package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package patterns

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.parsing.expressions.PostfixExpr

/**
* @author Alexander Podkhalyuzin
* Date: 28.02.2008
*/
object Guard extends Guard {
  override protected val postfixExpr = PostfixExpr
}

trait Guard {
  protected val postfixExpr: PostfixExpr

  def parse(builder: ScalaPsiBuilder): Boolean = parse(builder, noIf = false) //deprecated if true
  def parse(builder: ScalaPsiBuilder, noIf: Boolean): Boolean = {
    val guardMarker = builder.mark
    builder.getTokenType match {
      case ScalaTokenTypes.kIF =>
        builder.advanceLexer //Ate if
      case _ =>
        if (!noIf) {
          guardMarker.drop()
          return false
        }
    }
    if (!postfixExpr.parse(builder)) {
      if (noIf) {
        guardMarker.drop()
        return false
      }
      builder error ErrMsg("wrong.postfix.expression")
    }
    guardMarker.done(ScalaElementTypes.GUARD)
    return true
  }
}