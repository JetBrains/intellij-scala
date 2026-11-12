package org.jetbrains.plugins.scala.lang.parser.parsing.types.cc

import com.intellij.lang.PsiBuilder
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.lang.lexer.{ScalaKeywordTokenType, ScalaTokenType, ScalaTokenTypes}
import org.jetbrains.plugins.scala.lang.parser.{ErrMsg, ScalaElementType, ScalaPsiBuilderExt}
import org.jetbrains.plugins.scala.lang.parser.parsing.ParsingRule
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.parsing.top.QualId
import org.jetbrains.plugins.scala.lang.parser.parsing.types.StableId

import scala.annotation.tailrec

/**
 * Capture-reference syntax with capture checking enabled.
 *
 * == Scala 3.10.1 ==
 * {{{
 * SimpleRef    ::= id
 *                | [id '.'] 'this'
 *                | [id '.'] 'super' [ClassQualifier] '.' id
 * CaptureRef   ::= { SimpleRef '.' } SimpleRef [OnlyFilter] {ExceptFilter} ['.' 'rd']
 * OnlyFilter   ::= '.' 'only' '[' QualId ']'
 * ExceptFilter ::= '.' 'except' '[' QualId ']'
 * }}}
 * This describes accepted syntax: unlike the compiler's grammar comment, it excludes reach '*',
 * which the Scala 3.10.1 implementation recognizes only to report that it is no longer supported.
 *
 * == Older syntax retained for compatibility ==
 * Scala 3.9.0 supports reach capabilities, with the same SimpleRef and OnlyFilter rules:
 * {{{
 * CaptureRef ::= { SimpleRef '.' } SimpleRef ['*'] [OnlyFilter] ['.' 'rd']
 * }}}
 * The plugin's historical grammar from SCL-24630 is also retained:
 * {{{
 * CaptureRef ::= { SimpleRef '.' } SimpleRef ['*'] [AsFilter] ['.' 'rd']
 * AsFilter   ::= '.' 'as' '[' QualId ']'
 * }}}
 * The '.as' form describes historical plugin behavior, not Scala 3.8.0 compiler syntax:
 * that compiler's grammar comment named 'as', but its implementation already recognized 'only'.
 *
 * Plugin parsing and diagnostics accept the combined syntax without per-version checks.
 *
 * @see <a href="https://github.com/scala/scala3/blob/3.10.1/compiler/src/dotty/tools/dotc/parsing/Parsers.scala#L1810">Scala 3.10.1 capture-reference parser</a>
 * @see <a href="https://github.com/scala/scala3/blob/3.9.0/compiler/src/dotty/tools/dotc/parsing/Parsers.scala#L1672">Scala 3.9.0 capture-reference parser</a>
 * @see <a href="https://github.com/scala/scala3/blob/3.8.0/compiler/src/dotty/tools/dotc/parsing/Parsers.scala#L1636">Scala 3.8.0 capture-reference parser and its stale grammar comment</a>
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

    // Parse and consume the optional '*' reach marker for compatibility with older Scala versions.
    builder.tryParseSoftKeyword(ScalaTokenType.ReachCapabilityStar)

    val parsedAs = parseCaptureFilter(ScalaTokenType.AsKeyword)
    val parsedFilter = parsedAs || parseCapabilityFilters()
    parseReadOnly(parsedAs, parsedFilter)

    marker.done(ScalaElementType.CAPTURE_REF)
    true
  }

  private def parseCapabilityFilters()(implicit builder: ScalaPsiBuilder): Boolean = {
    var parsedFilter = parseCaptureFilter(ScalaTokenType.OnlyCapabilityKeyword)
    while (parseCaptureFilter(ScalaTokenType.ExceptCapabilityKeyword)) {
      parsedFilter = true
    }
    parsedFilter
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
            case "rd" | "as" | "only" | "except" =>
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

  private def parseCaptureFilter(keyword: ScalaKeywordTokenType)(implicit builder: ScalaPsiBuilder): Boolean = {
    if (builder.getTokenType != ScalaTokenTypes.tDOT) {
      return false
    }

    val marker = builder.mark()
    builder.advanceLexer() // eat .

    if (!builder.tryParseSoftKeyword(keyword)) {
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

  private def parseReadOnly(parsedAs: Boolean, parsedFilter: Boolean)(implicit builder: ScalaPsiBuilder): Unit = {
    if (builder.getTokenType != ScalaTokenTypes.tDOT) {
      return
    }

    builder.advanceLexer() // eat .

    if (!builder.tryParseSoftKeyword(ScalaTokenType.ReadOnlyCapabilityKeyword)) {
      if (parsedAs) {
        builder.error(ScalaBundle.message("rd.expected"))
      } else if (parsedFilter) {
        builder.error(ScalaBundle.message("except.or.rd.expected"))
      } else {
        builder.error(ScalaBundle.message("capture.filter.or.rd.expected"))
      }
    }
  }
}
