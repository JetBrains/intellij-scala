package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package patterns

import com.intellij.lang.PsiBuilder
import expressions.PostfixExpr
import lexer.ScalaTokenTypes

/**
* @author Alexander Podkhalyuzin
* Date: 28.02.2008
*/

object Guard {
  def parse(builder: PsiBuilder): Boolean = parse(builder, false) //deprecated if true
  def parse(builder: PsiBuilder, noIf: Boolean): Boolean = {
    val guardMarker = builder.mark
    builder.getTokenType match {
      case ScalaTokenTypes.kIF => {
        builder.advanceLexer //Ate if
      }
      case _ => {
        if (!noIf) {
          guardMarker.drop()
          return false
        }
      }
    }
    if (!PostfixExpr.parse(builder)) {
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