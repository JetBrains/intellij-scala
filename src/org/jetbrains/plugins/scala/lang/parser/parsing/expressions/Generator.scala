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
object Generator extends Generator {
  override protected val expr = Expr
  override protected val guard = Guard
  override protected val pattern1 = Pattern1
}

trait Generator {
  protected val expr: Expr
  protected val guard: Guard
  protected val pattern1: Pattern1

  def parse(builder: ScalaPsiBuilder): Boolean = {
    val genMarker = builder.mark
    if (builder.getTokenType == ScalaTokenTypes.kVAL) builder.advanceLexer() //deprecated
    if (!pattern1.parse(builder)) {
      genMarker.drop()
      return false
    }
    builder.getTokenType match {
      case ScalaTokenTypes.tCHOOSE =>
        builder.advanceLexer
      case _ =>
        builder error ErrMsg("choose.expected")
    }
    if (!expr.parse(builder)) builder error ErrMsg("wrong.expression")
    genMarker.done(ScalaElementTypes.GENERATOR)
    builder.getTokenType match {
      case ScalaTokenTypes.kIF => guard parse builder
      case _ =>
    }
    return true
  }
}