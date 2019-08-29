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
object SimplePattern {

  def parse(builder: ScalaPsiBuilder): Boolean = {
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
        if (Patterns.parse(builder)) {
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
        if (Pattern parse builder) {
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
      case _ =>
    }

    if (InterpolationPattern parse builder) {
      simplePatternMarker.done(ScalaElementType.INTERPOLATION_PATTERN)
      return true
    }

    if (Literal parse builder) {
      simplePatternMarker.done(ScalaElementType.LITERAL_PATTERN)
      return true
    }

    if (XmlPattern.parse(builder)) {
      simplePatternMarker.drop()
      return true
    }

    if (builder.lookAhead(ScalaTokenTypes.tIDENTIFIER) &&
      !builder.lookAhead(1, ScalaTokenTypes.tDOT) &&
      !builder.lookAhead(1, ScalaTokenTypes.tLPARENTHESIS) &&
      builder.invalidVarId
    ) {
      val rpm = builder.mark
      builder.getTokenText
      builder.advanceLexer()
      rpm.done(ScalaElementType.REFERENCE_PATTERN)
      simplePatternMarker.drop()
      return true
    }

    val rb1 = builder.mark
    if (StableId parse(builder, ScalaElementType.REFERENCE_EXPRESSION)) {
      builder.getTokenType match {
        case ScalaTokenTypes.tLPARENTHESIS =>
          rb1.rollbackTo()
          StableId.parse(builder, ScalaElementType.REFERENCE)
          val args = builder.mark
          builder.advanceLexer() //Ate (
          builder.disableNewlines()

          // TODO: add annotate as error in scala 3.0 remember that:
          //  "Under the -language:Scala2 option, Dotty will accept both the old and the new syntax.
          //  A migration warning will be emitted when the old syntax is encountered."
          // _* (Scala 2)
          def parseSeqWildcard(): Boolean = {
            if (builder.lookAhead(ScalaTokenTypes.tUNDER, ScalaTokenTypes.tIDENTIFIER)) {
              ParserUtils.eatShortSeqWildcardNext(builder)
            } else {
              false
            }
          }

          // TODO: add annotate as error in scala 3.0
          // xs @ _* (Scala 2)
          def parseSeqWildcardBinding(): Boolean = {
            val condition =
              builder.lookAhead(ScalaTokenTypes.tIDENTIFIER, ScalaTokenTypes.tAT, ScalaTokenTypes.tUNDER, ScalaTokenTypes.tIDENTIFIER) ||
                builder.lookAhead(ScalaTokenTypes.tUNDER, ScalaTokenTypes.tAT, ScalaTokenTypes.tUNDER, ScalaTokenTypes.tIDENTIFIER)

            if (condition) {
              ParserUtils.parseVarIdWithWildcardBinding(builder, withComma = false)
            } else {
              false
            }
          }

          // TODO: add annotate as error in scala 2.x
          // _ : _* (Scala 3)
          def parseSeqWildcardScala3(): Boolean = {
            val condition =
              builder.lookAhead(ScalaTokenTypes.tIDENTIFIER, ScalaTokenTypes.tCOLON, ScalaTokenTypes.tUNDER, ScalaTokenTypes.tIDENTIFIER) ||
                builder.lookAhead(ScalaTokenTypes.tUNDER, ScalaTokenTypes.tCOLON, ScalaTokenTypes.tUNDER, ScalaTokenTypes.tIDENTIFIER)

            if (condition) {
              // NOTE: There are several ways of emulating new Scala 3 wildcard pattern syntax:
              // 1) via already existing `SEQ_WILDCARD` (ScSeqWildcard) by allowing it to be in two forms: with `_ :` prefix and without
              // cons: if we have `xs: _*` pattern xs will not be resolved later
              // 2) via `TYPED_PATTERN` (ScTypePattern), it looks like some special form of it with some special type `_*`
              // cons: xs type `_*` is not resolved, thus any method calls on sequence will not work
              // 3) via `NAMING_PATTERN` (ScNamingPattern) (USED NOW), it make sence to do it due to semantically
              // `xs: _*` construction is the same as binding sequence tail to variable `xs`
              // pros: resolve and type inference on `xs` works automatically
              ParserUtils.parseVarIdWithWildcardBinding(builder, withComma = false)
            } else {
              false
            }
          }

          def parseSeqWildcardAny(): Boolean = parseSeqWildcard() ||  parseSeqWildcardScala3() || parseSeqWildcardBinding()

          // FIXME: we do not allow wildcards in the beginning (e.g. case List(_*, 42) => ???, but allow them
          //  in the middle (e.g. case List(1, _*, 2, _*) => ???) and do not highlight any error
          //  (compiler says Error: bad simple pattern: bad use of _* (a sequence pattern must be the last pattern))
          //  we should whether not parse wildcards in the middle OR (the best IMO) move the error to annotator
          if (!parseSeqWildcardAny()) {
            if (Pattern.parse(builder)) {
              while (builder.getTokenType == ScalaTokenTypes.tCOMMA) {
                builder.advanceLexer() // eat comma
                if (!parseSeqWildcardAny()) {
                  Pattern.parse(builder)
                }
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