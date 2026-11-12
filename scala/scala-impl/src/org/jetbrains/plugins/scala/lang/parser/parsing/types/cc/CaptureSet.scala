package org.jetbrains.plugins.scala.lang.parser.parsing.types.cc

import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenType.{ClassKeyword, GivenKeyword, ObjectKeyword, TraitKeyword}
import org.jetbrains.plugins.scala.lang.parser.{ErrMsg, PsiBuilderExt, ScalaElementType, ScalaPsiBuilderExt}
import org.jetbrains.plugins.scala.lang.parser.parsing.ParsingRule
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.util.ParserUtils

object CaptureSet extends ParsingRule {
  /**
   * Bounds must leave enclosing parameter-list delimiters and following declarations
   * intact when the closing brace is missing. Other capture-set contexts retain
   * their existing recovery through [[parse]].
   */
  def parseBound()(implicit builder: ScalaPsiBuilder): Unit = {
    import ScalaTokenTypes._

    val marker = builder.mark()
    builder.advanceLexer() // consume '{'
    var more = true
    while (more && !atBoundBoundary) {
      more = CaptureRef() && consumeBoundComma()
    }

    if (builder.getTokenType == tRBRACE) builder.advanceLexer()
    else builder.error(ErrMsg("rbrace.expected"))
    marker.done(ScalaElementType.CAPTURE_SET)
  }

  private def consumeBoundComma()(implicit builder: ScalaPsiBuilder): Boolean = {
    import ScalaTokenTypes._

    if (builder.getTokenType != tCOMMA || nextStartsParameter || builder.consumeTrailingComma(tRBRACE)) false
    else {
      builder.advanceLexer()
      // Only a multiline trailing comma may end the set without another capture.
      if (builder.getTokenType == tRBRACE) builder.error(ScalaBundle.message("capture.reference.expected"))
      true
    }
  }

  private def atBoundBoundary(implicit builder: ScalaPsiBuilder): Boolean = {
    import ScalaTokenTypes._

    builder.getTokenType match {
      case null | `tRBRACE` | `tRSQBRACKET` | `tLPARENTHESIS` | `tRPARENTHESIS` |
           `tLOWER_BOUND` | `tUPPER_BOUND` | `tASSIGN` | `tSEMICOLON` |
           `kDEF` | `kVAL` | `kVAR` | `kTYPE` | ClassKeyword | TraitKeyword | ObjectKeyword | GivenKeyword => true
      case _ => false
    }
  }

  // A comma alone is ambiguous: it may separate captures or parameters. An annotation,
  // variance followed by a name, or a name followed by '^', bounds or nested parameters
  // identifies a parameter. '+' and '-' can also name captures; a following '*'
  // is their legacy reach suffix rather than an unambiguous parameter name.
  // A backticked '*' still identifies a parameter name after variance.
  private def nextStartsParameter(implicit builder: ScalaPsiBuilder): Boolean = {
    import ScalaTokenTypes._

    builder.predict { next =>
      if (next.getTokenType == tAT) true
      else if (next.getTokenType != tIDENTIFIER && next.getTokenType != tUNDER) false
      else {
        val startsWithVariance = next.getTokenText == "+" || next.getTokenText == "-"
        next.advanceLexer()

        val hasParameterAfterVariance = startsWithVariance && next.getTokenText != "*" &&
          (next.getTokenType == tIDENTIFIER || next.getTokenType == tUNDER)
        hasParameterAfterVariance || atParameterSuffix(next)
      }
    }
  }

  private def atParameterSuffix(builder: ScalaPsiBuilder): Boolean = {
    import ScalaTokenTypes._

    builder.getTokenText == "^" || (builder.getTokenType match {
      case `tLOWER_BOUND` | `tUPPER_BOUND` | `tVIEW` | `tCOLON` | `tLSQBRACKET` => true
      case _ => false
    })
  }

  override def parse(implicit builder: ScalaPsiBuilder): Boolean = {
    if (builder.getTokenType == ScalaTokenTypes.tLBRACE) {
      val marker = builder.mark()
      builder.advanceLexer()

      if (builder.getTokenType == ScalaTokenTypes.tRBRACE) {
        builder.advanceLexer()
      } else {
        ParserUtils.parseLoopUntilRBrace(braceReported = true) {
          while({
            CaptureRef()
            val nextToken = builder.getTokenType
            if (nextToken == ScalaTokenTypes.tCOMMA) {
              builder.advanceLexer()
              true
            } else {
              if (nextToken != ScalaTokenTypes.tRBRACE) {
                builder.error(ScalaBundle.message("comma.or.rbrace.expected"))

                if (nextToken == null || nextToken == ScalaTokenTypes.tLBRACE) {
                  // if we find a '{' let parseLoopUntilRBrace handle it
                  false
                } else {
                  builder.advanceLexer()
                  true
                }
              } else {
                false
              }
            }
          }) ()
        }
      }
      marker.done(ScalaElementType.CAPTURE_SET)
      true
    } else {
      false
    }
  }
}
