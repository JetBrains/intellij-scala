package org.jetbrains.plugins.scala.lang.parser.parsing.params

import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenType.GivenKeyword
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.{ErrMsg, ScalaElementType}


/*
 * GivenParamClause    ::=  ‘given’ (‘(’ Params ‘)’ | GivenTypes)
 *
 */
object GivenParamClause {
  def parse(alreadyHadGivenClause: Boolean)(implicit builder: ScalaPsiBuilder): Boolean = {
    val marker = builder.mark()

    if (builder.twoNewlinesBeforeCurrentToken) {
      marker.drop()
      return false
    }

    val hadGivenKeyword =
      if (GivenKeyword.isCurrentToken(builder)) {
        GivenKeyword.remapCurrentToken()
        builder.advanceLexer() // Ate given
        true
      } else {
        if (!alreadyHadGivenClause) {
          marker.drop()
          return false
        }
        // we already found a given
        builder error ErrMsg("given.keyword.expected")
        false
      }

    // try parse given types first, because they also might start with '('
    // example:
    //   def test given (TupleFstType, TupleSndType) = ...
    if (hadGivenKeyword && GivenTypes.parse()) {
      marker.done(ScalaElementType.ANONYMOUS_GIVEN_PARAM_CLAUSE)
      return true
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
