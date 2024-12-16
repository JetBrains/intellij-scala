package org.jetbrains.plugins.scala.lang.parser.parsing

import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.{ErrMsg, ScalaElementType}
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.parsing.expressions.PostfixExpr

object Blub {
  def parse(implicit builder: ScalaPsiBuilder): Boolean = {
    val guardMarker = builder.mark()
    if (builder.getTokenType == ScalaTokenTypes.kIF) {
      builder.advanceLexer() //Ate if
    } else {
      guardMarker.drop()
      return false
    }

    if (!expr()) {
      builder.error(ScalaBundle.message("wrong.postfix.expression"))
    }
    guardMarker.done(ScalaElementType.GUARD)
    true
  }

  def expr()(implicit builder: ScalaPsiBuilder): Boolean = {
    val m = builder.mark()
    if (builder.getTokenType == ScalaTokenTypes.tIDENTIFIER) {
      builder.advanceLexer()
      m.done(ScalaElementType.REFERENCE)
      true
    } else {
      m.rollbackTo()
      false
    }
  }
}

/*object Blub extends ParsingRule {

  override def parse(implicit builder: ScalaPsiBuilder): Boolean = {
    while (builder.getTokenType == ScalaTokenTypes.kIF) {
      val m = builder.mark()
      builder.advanceLexer()
      if (expr()) {
        m.done(ScalaElementType.IF_STMT)
      } else {
        m.rollbackTo()
      }
    }
    true
  }

  def expr()(implicit builder: ScalaPsiBuilder): Boolean = {
    val m = builder.mark()
    if (builder.getTokenType == ScalaTokenTypes.tIDENTIFIER) {
      builder.advanceLexer()
      m.done(ScalaElementType.REFERENCE)
      true
    } else {
      m.rollbackTo()
      false
    }
  }
}*/
