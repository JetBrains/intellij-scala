package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package expressions

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.parsing.patterns.{Guard, Pattern1}

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
    if (builder.getTokenType == ScalaTokenTypes.kVAL) builder.advanceLexer() //deprecated
    if (!Pattern1.parse(builder)) {
      genMarker.drop()
      return false
    }
    builder.getTokenType match {
      case ScalaTokenTypes.tCHOOSE =>
        builder.advanceLexer()
      case _ =>
        builder error ErrMsg("choose.expected")
    }
    if (!Expr.parse(builder)) builder error ErrMsg("wrong.expression")
    genMarker.done(ScalaElementType.GENERATOR)
    builder.getTokenType match {
      case ScalaTokenTypes.kIF => Guard parse builder
      case _ =>
    }
    true
  }
}