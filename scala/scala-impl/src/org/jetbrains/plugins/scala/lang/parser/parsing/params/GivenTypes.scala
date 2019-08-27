package org.jetbrains.plugins.scala.lang.parser.parsing.params

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ErrMsg
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.parsing.types.AnnotType

/*
  GivenTypes        ::=  AnnotType {‘,’ AnnotType}
 */
object GivenTypes {
  def parse(builder: ScalaPsiBuilder): Boolean = {
    if (!AnnotType.parse(builder, isPattern = false, failOnError = true)) {
      return false
    }

    while (builder.getTokenType == ScalaTokenTypes.tCOMMA) {
      builder.advanceLexer() // Ate ,

      if (!AnnotType.parse(builder, isPattern = false)) {
        builder error ErrMsg("expected.given.parameter")
      }
    }

    true
  }
}
