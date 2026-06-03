package org.jetbrains.plugins.scala.lang.parser.parsing.expressions

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementType
import org.jetbrains.plugins.scala.lang.parser.parsing.ParsingRule
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder

/*
 * SelfInvocation ::= 'this' ArgumentExprs {ArgumentExprs}
 */
object SelfInvocation extends ParsingRule {

  override def parse(implicit builder: ScalaPsiBuilder): Boolean = {
    val selfMarker = builder.mark()
    builder.getTokenType match {
      case ScalaTokenTypes.kTHIS =>
        builder.advanceLexer() //Ate this
      case _ =>
        selfMarker.drop()
        return false
    }
    if (ArgumentExprs()) {
      while (!builder.newlineBeforeCurrentToken && ArgumentExprs()) {}
    }
    selfMarker.done(ScalaElementType.SELF_INVOCATION)
    true
  }
}