package org.jetbrains.plugins.scala.lang.parser.parsing.params

import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.lang.lexer.{ScalaTokenType, ScalaTokenTypes}
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.{ErrMsg, ScalaElementType}


/*
 * GivenParamClause    ::=  ‘given’ (‘(’ Params ‘)’ | GivenTypes)
 *
 */
object GivenParamClause {
  def parse(builder: ScalaPsiBuilder, alreadyHadGivenClause: Boolean): Boolean = {
    val marker = builder.mark()

    if (builder.twoNewlinesBeforeCurrentToken) {
      marker.drop()
      return false
    }

    val hadGivenKeyword =
      if (builder.getTokenText != ScalaTokenType.Given.debugName) {
        if (!alreadyHadGivenClause) {
          marker.drop()
          return false
        }
        // we already found a given
        builder error ErrMsg("given.keyword.expected")
        false
      } else {
        builder.remapCurrentToken(ScalaTokenType.Given)
        builder.advanceLexer() // Ate given
        true
      }

    builder.getTokenType match {
      case ScalaTokenTypes.tLPARENTHESIS =>
        builder.advanceLexer() //Ate (
      case _ if hadGivenKeyword =>
        builder error ScalaBundle.message("param.clause.expected")
        marker.drop()
        return false
      case _ =>
        marker.rollbackTo()
        return false
    }
    builder.withDisabledNewlines {
      Params parse builder
      builder.getTokenType match {
        case ScalaTokenTypes.tRPARENTHESIS =>
          builder.advanceLexer() //Ate )
        case _ =>
          builder error ScalaBundle.message("rparenthesis.expected")
      }
    }
    marker.done(ScalaElementType.PARAM_CLAUSE)
    true
  }
}
