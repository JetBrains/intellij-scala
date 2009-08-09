package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package expressions

import com.intellij.lang.PsiBuilder
import lexer.ScalaTokenTypes
import patterns.{Pattern1, Guard}

/**
* @author Alexander Podkhalyuzin
* Date: 06.03.2008
*/

/*
 * Generator ::= Pattern1 '<-' Expr [Guard]
 */

object Generator {
  def parse(builder: PsiBuilder): Boolean = {
    val genMarker = builder.mark
    if (builder.getTokenType == ScalaTokenTypes.kVAL) builder.advanceLexer //deprecated
    if (!Pattern1.parse(builder)) {
      genMarker.drop
      return false
    }
    builder.getTokenText match {
      case "<-" => {
        builder.advanceLexer
      }
      case _ => {
        builder error ErrMsg("choose.expected")
      }
    }
    if (!Expr.parse(builder)) builder error ErrMsg("wrong.expression")
    builder.getTokenType match {
      case ScalaTokenTypes.kIF => Guard parse builder
      case _ => {}
    }
    genMarker.done(ScalaElementTypes.GENERATOR)
    return true
  }
}