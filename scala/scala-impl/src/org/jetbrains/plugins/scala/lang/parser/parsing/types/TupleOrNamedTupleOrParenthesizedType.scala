package org.jetbrains.plugins.scala.lang.parser.parsing.types

import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.{ErrMsg, ScalaElementType}
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder

import scala.annotation.tailrec

object TupleOrNamedTupleOrParenthesizedType {
  def apply(isPattern: Boolean, typeVariables: Boolean)(implicit builder: ScalaPsiBuilder): Unit = builder.withDisabledNewlines {
    assert(builder.getTokenType == ScalaTokenTypes.tLPARENTHESIS)

    val tupleMarker = builder.mark()
    builder.advanceLexer()
    val contentType = parseContent(isPattern, typeVariables)

    val hadTrailingComma = builder.getTokenType == ScalaTokenTypes.tCOMMA
    if (hadTrailingComma) {
      builder.advanceLexer()
    }

    builder.getTokenType match {
      case ScalaTokenTypes.tRPARENTHESIS =>
        if (hadTrailingComma && !contentType.allowsTrailingComma) {
          builder.error(ScalaBundle.message("identifier.expected.comma.found"))
        }
        builder.advanceLexer() //Ate )
      case _ =>
        builder.error(ScalaBundle.message("rparenthesis.expected"))
    }
    tupleMarker.done(contentType.elementType)
  }

  private abstract class ContentType(val isTuple: Boolean, val elementType: ScalaElementType) {
    def allowsTrailingComma: Boolean = isTuple
  }
  private object ContentType {
    case object Parenthesized extends ContentType(isTuple = false, elementType = ScalaElementType.TYPE_IN_PARENTHESIS)
    case object Tuple extends ContentType(isTuple = true, elementType = ScalaElementType.TUPLE_TYPE)
    case object NamedTuple extends ContentType(isTuple = true, elementType = ScalaElementType.NAMED_TUPLE_TYPE)
  }


  private def parseContent(isPattern: Boolean, typeVariables: Boolean)(implicit builder: ScalaPsiBuilder): ContentType = {
    var contentType =
      if (builder.features.`named tuples` && builder.lookAhead(ScalaTokenTypes.tIDENTIFIER, ScalaTokenTypes.tCOLON)) {
        ContentType.NamedTuple
      } else {
        ContentType.Parenthesized
      }

    def parseComponent(): Boolean = {
      val componentMarker = builder.mark()
      if (contentType == ContentType.NamedTuple) {
        if (builder.getTokenType == ScalaTokenTypes.tIDENTIFIER) {
          builder.advanceLexer()

          if (builder.getTokenType == ScalaTokenTypes.tCOLON) {
            builder.advanceLexer()
          } else {
            builder.error(ErrMsg("colon.expected"))
          }
        } else {
          builder.error(ErrMsg("identifier.expected"))

          if (builder.getTokenType == ScalaTokenTypes.tCOLON) {
            builder.advanceLexer()
          }
        }
      }

      val parsedType = ParamType.parseWithoutScParamTypeCreation(isPattern, typeVariables)(builder)

      if (contentType == ContentType.NamedTuple) {
        componentMarker.done(ScalaElementType.NAMED_TUPLE_TYPE_COMPONENT)
      } else {
        componentMarker.drop()
      }

      parsedType || contentType == ContentType.NamedTuple
    }

    val typesMarker = builder.mark()
    if (parseComponent()) {
      @tailrec
      def parseCommaAndNexts(): Unit = {
        if (builder.getTokenType == ScalaTokenTypes.tCOMMA && !builder.consumeTrailingComma(ScalaTokenTypes.tRPARENTHESIS)) {
          builder.advanceLexer() //Ate ,

          if (!contentType.isTuple) {
            contentType = ContentType.Tuple
          }

          parseComponent()
          parseCommaAndNexts()
        }
      }

      parseCommaAndNexts()
      if (contentType == ContentType.Tuple) {
        // NamedTuple doesn't use types
        // TODO: remove ScTypes altogether
        typesMarker.done(ScalaElementType.TYPES)
      } else {
        typesMarker.drop()
      }
    } else {
      // not even the first component was correct
      typesMarker.drop()
    }

    contentType
  }
}