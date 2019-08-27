package org.jetbrains.plugins.scala.lang.parser.parsing.params

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.parsing.types.AnnotType
import org.jetbrains.plugins.scala.lang.parser.{ErrMsg, ScalaElementType}

/*
  GivenTypes        ::=  AnnotType {‘,’ AnnotType}
 */
object GivenTypes {
  def parse(builder: ScalaPsiBuilder): Boolean = {
    if (!parseAnonymousGivenParameter(builder, failOnError = true)) {
      return false
    }

    while (builder.getTokenType == ScalaTokenTypes.tCOMMA) {
      builder.advanceLexer() // Ate ,

      if (!parseAnonymousGivenParameter(builder, failOnError = false)) {
        builder error ErrMsg("expected.given.parameter")
      }
    }

    true
  }

  private def parseAnonymousGivenParameter(builder: ScalaPsiBuilder, failOnError: Boolean): Boolean = {
    val marker = builder.mark()
    val result = AnnotType.parse(builder, isPattern = false, failOnError = failOnError)

    if (result) {
       marker.done(ScalaElementType.ANONYMOUS_GIVEN_PARAM)
    } else {
      marker.rollbackTo()
    }

    result
  }
}
