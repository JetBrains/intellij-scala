package org.jetbrains.plugins.scala.lang.parser.parsing.types.cc

import com.intellij.lang.PsiBuilder
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.lang.lexer.{ScalaTokenType, ScalaTokenTypes}
import org.jetbrains.plugins.scala.lang.parser.{ErrMsg, ScalaElementType}
import org.jetbrains.plugins.scala.lang.parser.parsing.ParsingRule
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.parsing.top.QualId
import org.jetbrains.plugins.scala.lang.parser.parsing.types.StableId

import scala.annotation.tailrec

/**
 * SimpleRef   ::=  id
 *               |  [id ‘.’] ‘this’
 *               |  [id ‘.’] ‘super’ [ClassQualifier] ‘.’ id
 * CaptureRef  ::=  { SimpleRef ‘.’ } SimpleRef [‘*’] [CapFilter] [‘.’ ‘rd’] -- under captureChecking
 * CapFilter   ::=  ‘.’ ‘as’ ‘[’ QualId ’]’                                  -- under captureChecking
 */
object CaptureRef extends ParsingRule {

  override def parse(implicit builder: ScalaPsiBuilder): Boolean = {
    val marker = builder.mark()
    if (!parseSimpleRef()) {
      builder.error(ScalaBundle.message("capture.reference.expected"))
      if (builder.getTokenType != ScalaTokenTypes.tDOT) {
        marker.drop()
        return false
      }
    }

    // parse and eat *
    builder.tryParseSoftKeyword(ScalaTokenType.ReachCapabilityStar)

    val parsedAs = parseCaptureFilter()

    parseReadOnly(parsedAs)

    marker.done(ScalaElementType.CAPTURE_REF)
    true
  }

  private def parseSimpleRef()(implicit builder: ScalaPsiBuilder): Boolean = {
    @tailrec
    def parseNext(marker: PsiBuilder.Marker, parseDot: Boolean, hadOne: Boolean): Boolean = {
      if (parseDot) {
        if (builder.getTokenType != ScalaTokenTypes.tDOT) {
          marker.drop()
          true
        } else {
          val rollBackMarker = builder.mark()
          builder.advanceLexer()

          builder.getTokenText match {
            case "rd" | "as" =>
              rollBackMarker.rollbackTo()
              marker.drop()
              true
            case _ =>
              rollBackMarker.drop()
              parseNext(marker, parseDot = false, hadOne)
          }
        }
      } else {
        builder.getTokenType match {
          case ScalaTokenTypes.tIDENTIFIER =>
            builder.advanceLexer()
            marker.done(ScalaElementType.REFERENCE)
            parseNext(marker.precede(), parseDot = true, hadOne = true)
          case ScalaTokenTypes.kTHIS =>
            builder.advanceLexer()
            marker.done(ScalaElementType.THIS_REFERENCE)
            parseNext(marker.precede(), parseDot = true, hadOne = true)
          case ScalaTokenTypes.kSUPER =>
            // parse super[X].id or super.id
            builder.advanceLexer()
            val token = builder.getTokenType
            if (token != ScalaTokenTypes.tDOT && token != ScalaTokenTypes.tLSQBRACKET) {
              builder.error(ErrMsg("dot.or.cq.expected"))
              marker.done(ScalaElementType.SUPER_REFERENCE)
              return true
            }
            StableId.parseClassQualifier()
            marker.done(ScalaElementType.SUPER_REFERENCE)

            if (builder.getTokenType == ScalaTokenTypes.tDOT) {
              builder.advanceLexer() // eat .
            } else {
              builder.error(ErrMsg("dot.expected"))
            }

            val refMarker = marker.precede()
            if (builder.getTokenType != ScalaTokenTypes.tIDENTIFIER) {
              builder.error(ErrMsg("identifier.expected"))
              refMarker.done(ScalaElementType.REFERENCE)
              return true
            }
            builder.advanceLexer() // eat identifier
            refMarker.done(ScalaElementType.REFERENCE)
            parseNext(refMarker.precede(), parseDot = true, hadOne = true)
          case _ =>
            if (hadOne) {
              builder.error(ErrMsg("identifier.expected"))
              marker.done(ScalaElementType.REFERENCE)
            } else {
              marker.drop()
            }
            hadOne
        }
      }
    }

    parseNext(builder.mark(), parseDot = false, hadOne = false)
  }

  private def parseCaptureFilter()(implicit builder: ScalaPsiBuilder): Boolean = {
    if (builder.getTokenType != ScalaTokenTypes.tDOT) {
      return false
    }

    val marker = builder.mark()
    builder.advanceLexer() // eat .

    if (!builder.tryParseSoftKeyword(ScalaTokenType.AsKeyword)) {
      marker.rollbackTo()
      return false
    }

    if (builder.getTokenType != ScalaTokenTypes.tLSQBRACKET) {
      builder.error(ScalaBundle.message("lsqbracket.expected"))
      marker.drop()
      return true
    }

    builder.advanceLexer()

    val hadQualId = QualId()

    if (builder.getTokenType == ScalaTokenTypes.tRSQBRACKET) {
      builder.advanceLexer()
    } else if (hadQualId && builder.rawLookup(-1) != ScalaTokenTypes.tDOT) {
      // only give an error for missing rsqbracket if the qual didn't have a problem
      // so: .as[a.b  <- give error
      //     .as[a.b. <- don't give error, QualId already added an error
      builder.error(ScalaBundle.message("rsqbracket.expected"))
    }

    // only create a CaptureFilter if the id was parsed
    if (hadQualId) {
      marker.done(ScalaElementType.CAPTURE_FILTER)
    } else {
      marker.drop()
    }
    true
  }

  private def parseReadOnly(parsedAs: Boolean)(implicit builder: ScalaPsiBuilder): Unit = {
    if (builder.getTokenType != ScalaTokenTypes.tDOT) {
      return
    }

    builder.advanceLexer() // eat .

    if (!builder.tryParseSoftKeyword(ScalaTokenType.ReadOnlyCapabilityKeyword)) {
      if (parsedAs) {
        builder.error(ScalaBundle.message("rd.expected"))
      } else {
        builder.error(ScalaBundle.message("as.or.rd.expected"))
      }
    }
  }
}
