package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package expressions

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.parsing.patterns.{Guard, Pattern1}

/*
 * Generator ::= ['case'] Pattern1 '<-' Expr [Guard]
 */
object Generator extends ParsingRule {

  override def parse(implicit builder: ScalaPsiBuilder): Boolean = {
    val genMarker = builder.mark()
    if (builder.getTokenType == ScalaTokenTypes.kVAL) builder.advanceLexer() //deprecated
    builder.getTokenType match {
      case ScalaTokenTypes.kCASE => builder.advanceLexer()
      case _ =>
    }
    if (!Pattern1()) {
      genMarker.drop()
      return false
    }
    builder.getTokenType match {
      case ScalaTokenTypes.tCHOOSE =>
        builder.advanceLexer()

        if (!ExprInIndentationRegion()) {
          builder error ErrMsg("wrong.expression")
        }
        genMarker.done(ScalaElementType.GENERATOR)
        builder.getTokenType match {
          case ScalaTokenTypes.kIF => Guard()
          case _ =>
        }
        true
      case _ =>
        builder error ErrMsg("choose.expected")
        genMarker.drop()
        false
    }
  }
}