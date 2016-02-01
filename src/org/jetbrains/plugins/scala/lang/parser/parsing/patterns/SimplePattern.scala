package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package patterns

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.parsing.expressions.Literal
import org.jetbrains.plugins.scala.lang.parser.parsing.types.StableId
import org.jetbrains.plugins.scala.lang.parser.parsing.xml.pattern.XmlPattern
import org.jetbrains.plugins.scala.lang.parser.util.ParserUtils

/**
* @author Alexander Podkhalyuzin
* Date: 29.02.2008
*/

/*
 * SimplePattern ::= '_'
 *                 | varid
 *                 | Literal
 *                 | StableId
 *                 | StableId '(' [Patterns [',']] ')'
 *                 | StableId '(' [Patterns ','] [(varid | '_' ) '@'] '_' '*'')'
 *                 |'(' [Patterns [',']] ')'
 *                 | XmlPattern
 */
object SimplePattern extends SimplePattern {
  override protected val literal = Literal
  override protected val interpolationPattern = InterpolationPattern
  override protected val pattern = Pattern
  override protected val patterns = Patterns
}

trait SimplePattern extends ParserNode {
  protected val literal: Literal
  protected val pattern: Pattern
  protected val interpolationPattern: InterpolationPattern
  protected val patterns: Patterns

  def parse(builder: ScalaPsiBuilder): Boolean = {
    def isVarId = builder.getTokenText.substring(0, 1).toLowerCase ==
            builder.getTokenText.substring(0, 1) && !(
            builder.getTokenText.apply(0) == '`' && builder.getTokenText.apply(builder.getTokenText.length - 1) == '`'
            )
    val simplePatternMarker = builder.mark
    builder.getTokenType match {
      case ScalaTokenTypes.tUNDER =>
        builder.advanceLexer() //Ate _
        builder.getTokenText match {
          case "*" =>
            simplePatternMarker.rollbackTo()
            return false
          case _ =>
        }
        simplePatternMarker.done(ScalaElementTypes.WILDCARD_PATTERN)
        return true
      case ScalaTokenTypes.tLPARENTHESIS =>
        builder.advanceLexer() //Ate (
        builder.disableNewlines
        builder.getTokenType match {
          case ScalaTokenTypes.tRPARENTHESIS =>
            builder.advanceLexer() //Ate )
            builder.restoreNewlinesState
            simplePatternMarker.done(ScalaElementTypes.TUPLE_PATTERN)
            return true
          case _ =>
        }
        if (patterns parse builder) {
          builder.getTokenType match {
            case ScalaTokenTypes.tRPARENTHESIS =>
              builder.advanceLexer() //Ate )
              builder.restoreNewlinesState
              simplePatternMarker.done(ScalaElementTypes.TUPLE_PATTERN)
              return true
            case _ =>
              builder error ScalaBundle.message("rparenthesis.expected")
              builder.restoreNewlinesState
              simplePatternMarker.done(ScalaElementTypes.TUPLE_PATTERN)
              return true
          }
        }
        if (pattern parse builder) {
          builder.getTokenType match {
            case ScalaTokenTypes.tRPARENTHESIS =>
              builder.advanceLexer() //Ate )
            case _ =>
              builder error ScalaBundle.message("rparenthesis.expected")
          }
          builder.restoreNewlinesState
          simplePatternMarker.done(ScalaElementTypes.PATTERN_IN_PARENTHESIS)
          return true
        }
      case _ =>
    }
    if (interpolationPattern parse builder) {
      simplePatternMarker.done(ScalaElementTypes.INTERPOLATION_PATTERN)
      return true
    }

    if (literal parse builder) {
      simplePatternMarker.done(ScalaElementTypes.LITERAL_PATTERN)
      return true
    }

    if (XmlPattern.parse(builder)) {
      simplePatternMarker.drop()
      return true
    }
    if (lookAhead(builder, ScalaTokenTypes.tIDENTIFIER) &&
            !lookAhead(builder, ScalaTokenTypes.tIDENTIFIER, ScalaTokenTypes.tDOT) &&
            !lookAhead(builder, ScalaTokenTypes.tIDENTIFIER, ScalaTokenTypes.tLPARENTHESIS) &&
            isVarId) {
      val rpm = builder.mark
      builder.getTokenText
      builder.advanceLexer()
      rpm.done(ScalaElementTypes.REFERENCE_PATTERN)
      simplePatternMarker.drop()
      return true
    }

    val rb1 = builder.mark
    if (StableId parse (builder, ScalaElementTypes.REFERENCE_EXPRESSION)) {
      builder.getTokenType match {
        case ScalaTokenTypes.tLPARENTHESIS =>
          rb1.rollbackTo()
          StableId parse (builder, ScalaElementTypes.REFERENCE)
          val args = builder.mark
          builder.advanceLexer() //Ate (
          builder.disableNewlines

          def parseSeqWildcard(withComma: Boolean): Boolean = {
            if (if (withComma)
              lookAhead(builder, ScalaTokenTypes.tCOMMA, ScalaTokenTypes.tUNDER, ScalaTokenTypes.tIDENTIFIER)
            else lookAhead(builder, ScalaTokenTypes.tUNDER, ScalaTokenTypes.tIDENTIFIER)) {
              val wild = builder.mark
              if (withComma) builder.advanceLexer()
              builder.getTokenType
              builder.advanceLexer()
              if (builder.getTokenType == ScalaTokenTypes.tIDENTIFIER && "*".equals(builder.getTokenText)) {
                builder.advanceLexer()
                wild.done(ScalaElementTypes.SEQ_WILDCARD)
                true
              } else {
                wild.rollbackTo()
                false
              }
            } else {
              false
            }
          }

          def parseSeqWildcardBinding(withComma: Boolean): Boolean = {
            if (if (withComma) lookAhead(builder, ScalaTokenTypes.tCOMMA, ScalaTokenTypes.tIDENTIFIER, ScalaTokenTypes.tAT,
            ScalaTokenTypes.tUNDER, ScalaTokenTypes.tIDENTIFIER) || lookAhead(builder, ScalaTokenTypes.tCOMMA, ScalaTokenTypes.tUNDER, ScalaTokenTypes.tAT,
              ScalaTokenTypes.tUNDER, ScalaTokenTypes.tIDENTIFIER)
            else lookAhead(builder, ScalaTokenTypes.tIDENTIFIER, ScalaTokenTypes.tAT,
            ScalaTokenTypes.tUNDER, ScalaTokenTypes.tIDENTIFIER) || lookAhead(builder, ScalaTokenTypes.tUNDER, ScalaTokenTypes.tAT,
              ScalaTokenTypes.tUNDER, ScalaTokenTypes.tIDENTIFIER)) {
              val wild = builder.mark
              if (withComma) builder.advanceLexer() // ,
              builder.getTokenType
              if (isVarId) {
                builder.advanceLexer() // id
              } else {
                wild.rollbackTo()
                return false
              }
              builder.getTokenType
              builder.advanceLexer() // @
              builder.getTokenType
              if (ParserUtils.eatSeqWildcardNext(builder)) {
                wild.done(ScalaElementTypes.NAMING_PATTERN)
                return true
              }
              else {
                wild.rollbackTo()
                return false
              }
            }
            false
          }

          if (!parseSeqWildcard(withComma = false) && !parseSeqWildcardBinding(withComma = false) && pattern.parse(builder)) {
            while (builder.getTokenType == ScalaTokenTypes.tCOMMA) {
              builder.advanceLexer() // eat comma
              if (!parseSeqWildcard(withComma = false) && !parseSeqWildcardBinding(withComma = false)) pattern.parse(builder)
            }
          }
          builder.getTokenType match {
            case ScalaTokenTypes.tRPARENTHESIS =>
              builder.advanceLexer() //Ate )
            case _ =>
              builder error ErrMsg("rparenthesis.expected")
          }
          builder.restoreNewlinesState
          args.done(ScalaElementTypes.PATTERN_ARGS)
          simplePatternMarker.done(ScalaElementTypes.CONSTRUCTOR_PATTERN)
          return true
        case _ =>
          rb1.drop()
          simplePatternMarker.done(ScalaElementTypes.STABLE_REFERENCE_PATTERN)
          return true
      }
    }
    simplePatternMarker.rollbackTo()
    false
  }
}