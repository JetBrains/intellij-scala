package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package expressions

import com.intellij.lang.PsiBuilder
import lexer.ScalaTokenTypes
import patterns.{Pattern1, Guard}
import builder.ScalaPsiBuilder

/**
* @author Alexander Podkhalyuzin
* Date: 06.03.2008
*/

/*
 * Generator ::= Pattern1 '<-' Expr [Guard]
 */

object Generator {
  def parse(builder: ScalaPsiBuilder): Boolean = {
    val genMarker = builder.mark
    if (builder.getTokenType == ScalaTokenTypes.kVAL) builder.advanceLexer //deprecated
    if (!Pattern1.parse(builder)) {
      genMarker.drop
      return false
    }
    builder.getTokenType match {
      case ScalaTokenTypes.tCHOOSE => {
        builder.advanceLexer
      }
      case _ => {
        builder error ErrMsg("choose.expected")
      }
    }
    if (!Expr.parse(builder)) builder error ErrMsg("wrong.expression")
    genMarker.done(ScalaElementTypes.GENERATOR)
    builder.getTokenType match {
      case ScalaTokenTypes.kIF => Guard parse builder
      case _ => {}
    }
    return true
  }
}