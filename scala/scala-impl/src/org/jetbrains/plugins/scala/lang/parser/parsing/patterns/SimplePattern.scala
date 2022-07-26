package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package patterns

import org.jetbrains.plugins.scala.lang.lexer.{ScalaTokenType, ScalaTokenTypes}
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.parsing.expressions.{Literal, Quoted}
import org.jetbrains.plugins.scala.lang.parser.parsing.types.{CompoundType, StableId}
import org.jetbrains.plugins.scala.lang.parser.parsing.xml.pattern.XmlPattern
import org.jetbrains.plugins.scala.lang.parser.util.ParserUtils

/*
 * Scala 2.x
 * SimplePattern ::= '_'
 *                 | varid
 *                 | Literal
 *                 | StableId
 *                 | StableId '(' [Patterns [',']] ')'
 *                 | StableId '(' [Patterns ','] [(varid | '_' ) '@'] '_' '*'')'
 *                 |'(' [Patterns [',']] ')'
 *                 | XmlPattern
 *
 * Scala 3
 * SimplePattern ::=  PatVar
 *                 |  Literal
 *                 |  ‘(’ [Patterns] ‘)’
 *                 |  Quoted
 *                 |  XmlPattern
 *                 |  SimplePattern1 [TypeArgs] [ArgumentPatterns]
 *
 * PatVar           ::=  varid
 *                    |  ‘_’
 * SimplePattern1   ::=  Path
 *                    |  SimplePattern1 ‘.’ id
 * ArgumentPatterns ::=  ‘(’ [Patterns] ‘)’
 *                    |  ‘(’ [Patterns ‘,’] Pattern2 ‘:’ ‘_’ ‘*’ ‘)’
 */
object SimplePattern extends ParsingRule {

  override def parse(implicit builder: ScalaPsiBuilder): Boolean = {
    val simplePatternMarker = builder.mark()

    builder.getTokenType match {
      case ScalaTokenTypes.tUNDER =>
        builder.advanceLexer() //Ate _
        builder.getTokenText match {
          case "*" =>
            simplePatternMarker.rollbackTo()
            return false
          case _ =>
        }
        simplePatternMarker.done(ScalaElementType.WILDCARD_PATTERN)
        return true
      case ScalaTokenTypes.tLPARENTHESIS =>
        builder.advanceLexer() //Ate (
        builder.disableNewlines()
        builder.getTokenType match {
          case ScalaTokenTypes.tRPARENTHESIS =>
            builder.advanceLexer() //Ate )
            builder.restoreNewlinesState()
            simplePatternMarker.done(ScalaElementType.TUPLE_PATTERN)
            return true
          case _ =>
        }
        if (Patterns()) {
          builder.getTokenType match {
            case ScalaTokenTypes.tRPARENTHESIS =>
              builder.advanceLexer() //Ate )
              builder.restoreNewlinesState()
              simplePatternMarker.done(ScalaElementType.TUPLE_PATTERN)
              return true
            case _ =>
              builder error ScalaBundle.message("rparenthesis.expected")
              builder.restoreNewlinesState()
              simplePatternMarker.done(ScalaElementType.TUPLE_PATTERN)
              return true
          }
        }
        if (Pattern()) {
          builder.getTokenType match {
            case ScalaTokenTypes.tRPARENTHESIS =>
              builder.advanceLexer() //Ate )
            case _ =>
              builder error ScalaBundle.message("rparenthesis.expected")
          }
          builder.restoreNewlinesState()
          simplePatternMarker.done(ScalaElementType.PATTERN_IN_PARENTHESIS)
          return true
        }
      case ScalaTokenType.GivenKeyword =>
        builder.advanceLexer() //Ate given

        val hasTypeElement = CompoundType(isPattern = true)

        if (!hasTypeElement) {
          builder.error(ScalaBundle.message("type.expected"))
          simplePatternMarker.drop()
          return false
        } else {
          simplePatternMarker.done(ScalaElementType.GIVEN_PATTERN)
          return true
        }
      case _ =>
    }

    if (InterpolationPattern()) {
      simplePatternMarker.done(ScalaElementType.INTERPOLATION_PATTERN)
      return true
    }

    if (Literal()) {
      simplePatternMarker.done(ScalaElementType.LITERAL_PATTERN)
      return true
    }

    if (builder.getTokenType == ScalaTokenType.QuoteStart) {
      builder.enterQuotedPattern()
      try
        if (Quoted()) {
          simplePatternMarker.done(ScalaElementType.QUOTED_PATTERN)
          return true
        }
      finally
        builder.exitQuotedPattern()
    }

    if (XmlPattern()) {
      simplePatternMarker.drop()
      return true
    }

    if (builder.lookAhead(ScalaTokenTypes.tIDENTIFIER) &&
      !builder.lookAhead(1, ScalaTokenTypes.tDOT) &&
      !builder.lookAhead(1, ScalaTokenTypes.tLPARENTHESIS) &&
      builder.invalidVarId
    ) {
      val rpm = builder.mark()
      builder.getTokenText
      builder.advanceLexer()
      rpm.done(ScalaElementType.REFERENCE_PATTERN)
      simplePatternMarker.drop()
      return true
    }

    val rb1 = builder.mark()
    if (StableId(ScalaElementType.REFERENCE_EXPRESSION)) {
      builder.getTokenType match {
        case ScalaTokenTypes.tLPARENTHESIS =>
          rb1.rollbackTo()
          StableId(ScalaElementType.REFERENCE)
          val args = builder.mark()
          builder.advanceLexer() //Ate (
          builder.disableNewlines()

          // _* (Scala 2)
          def parseSeqWildcard(): Boolean = {
            if (builder.lookAhead(ScalaTokenTypes.tUNDER, ScalaTokenTypes.tIDENTIFIER)) {
              ParserUtils.eatShortSeqWildcardNext()
            } else {
              false
            }
          }

          // xs @ _* (Scala 2)
          def parseSeqWildcardBinding(): Boolean = {
            val condition =
              builder.lookAhead(ScalaTokenTypes.tIDENTIFIER, ScalaTokenTypes.tAT, ScalaTokenTypes.tUNDER, ScalaTokenTypes.tIDENTIFIER) ||
                builder.lookAhead(ScalaTokenTypes.tUNDER, ScalaTokenTypes.tAT, ScalaTokenTypes.tUNDER, ScalaTokenTypes.tIDENTIFIER)

            if (condition) {
              ParserUtils.parseVarIdWithWildcardBinding(withComma = false)
            } else {
              false
            }
          }

          // xs : _* (Scala 3 old?)
          def parseSeqWildcardBindingScala3_old(): Boolean = {
            // should not parse in scala 2, but let's do it anyway and annotate it in ScPatternTypeUnawareAnnotator
            val condition = (
              builder.lookAhead(ScalaTokenTypes.tIDENTIFIER, ScalaTokenTypes.tCOLON, ScalaTokenTypes.tUNDER, ScalaTokenTypes.tIDENTIFIER) ||
                builder.lookAhead(ScalaTokenTypes.tUNDER, ScalaTokenTypes.tCOLON, ScalaTokenTypes.tUNDER, ScalaTokenTypes.tIDENTIFIER)
              )

            if (condition) {
              ParserUtils.parseVarIdWithWildcardBinding(withComma = false)
            } else {
              false
            }
          }

          // xs* (Scala 3 new?)
          def parseSeqWildcardBindingScala3_new(): Boolean = {
            builder.getTokenType match {
              case ScalaTokenTypes.tIDENTIFIER | ScalaTokenTypes.tUNDER
                if builder.features.`Scala 3 vararg splice syntax` && builder.lookAhead(1, ScalaTokenTypes.tIDENTIFIER)  =>

                builder.lookAhead(2) match {
                  case ScalaTokenTypes.tRPARENTHESIS | ScalaTokenTypes.tCOMMA =>
                    val marker = builder.mark()
                    builder.advanceLexer() // ate id or _

                    if (builder.getTokenText == "*") {
                      builder.advanceLexer() // ate *
                      marker.done(ScalaElementType.SEQ_WILDCARD_PATTERN)
                      true
                    } else {
                      marker.rollbackTo()
                      false
                    }
                  case _ => false
                }

              case _ => false
            }
          }

          def parseSeqWildcardAny(): Boolean = parseSeqWildcard() ||  parseSeqWildcardBindingScala3_old() || parseSeqWildcardBindingScala3_new() || parseSeqWildcardBinding()

          if (parseSeqWildcardAny() || Pattern()) {
            while (builder.getTokenType == ScalaTokenTypes.tCOMMA) {
              builder.advanceLexer() // eat comma
              if (!parseSeqWildcardAny()) {
                Pattern()
              }
            }
          }

          builder.getTokenType match {
            case ScalaTokenTypes.tRPARENTHESIS =>
              builder.advanceLexer() //Ate )
            case _ =>
              builder error ErrMsg("rparenthesis.expected")
          }
          builder.restoreNewlinesState()
          args.done(ScalaElementType.PATTERN_ARGS)

          simplePatternMarker.done(ScalaElementType.CONSTRUCTOR_PATTERN)
          true
        case _ =>
          rb1.drop()
          simplePatternMarker.done(ScalaElementType.StableReferencePattern)
          true
      }
    } else {
      simplePatternMarker.rollbackTo()
      false
    }
  }
}