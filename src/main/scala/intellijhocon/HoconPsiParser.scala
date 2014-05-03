package intellijhocon

import com.intellij.lang.{WhitespacesBinders, PsiBuilder, PsiParser}
import com.intellij.psi.tree.IElementType
import scala.util.matching.Regex
import com.intellij.lang.PsiBuilder.Marker

class HoconPsiParser extends PsiParser {

  import HoconTokenType._
  import HoconTokenSets._
  import HoconElementType._

  def parse(root: IElementType, builder: PsiBuilder) = {
    val file = builder.mark()
    new Parser(builder).parseFile()
    file.done(root)
    builder.getTreeBuilt
  }

  val IncludeQualifiers = Set("url(", "classpath(", "file(")
  val IntegerPattern = """-?(0|[1-9][0-9]*)""".r
  val DecimalPartPattern = """([0-9]+)((e|E)(\+|-)?[0-9]+)?""".r

  class Parser(builder: PsiBuilder) {

    def newLineBeforeCurrentToken =
      builder.rawLookup(-1) == LineBreakingWhitespace

    def matches(matcher: Matcher) =
      (matcher.tokenSet.contains(builder.getTokenType) && (!matcher.requireNoNewLine || !newLineBeforeCurrentToken)) ||
        (matcher.matchNewLine && newLineBeforeCurrentToken) || (matcher.matchEof && builder.eof)

    def matchesUnquoted(str: String) =
      matches(UnquotedChars) && builder.getTokenText == str

    def matchesUnquoted(pattern: Regex) =
      matches(UnquotedChars) && pattern.pattern.matcher(builder.getTokenText).matches

    def pass(matcher: Matcher): Boolean = {
      val result = matches(matcher)
      if (result && (!matcher.matchNewLine || !newLineBeforeCurrentToken) && (!matcher.matchEof || !builder.eof)) {
        builder.advanceLexer()
      }
      result
    }

    def errorUntil(matcher: Matcher, msg: String, onlyNonEmpty: Boolean = false) {
      if (!onlyNonEmpty || !matches(matcher)) {
        val marker = builder.mark()
        while (!matches(matcher)) {
          builder.advanceLexer()
        }
        marker.error(msg)
      }
    }

    def tokenError(msg: String) {
      val marker = builder.mark()
      builder.advanceLexer()
      marker.error(msg)
    }

    def parseFile() {
      if (matches(LBrace))
        parseObject()
      else
        parseObjectEntries(insideObject = false)
      errorUntil(Empty.orEof, "expected end of file", onlyNonEmpty = true)
    }

    def parseObject() = {
      val marker = builder.mark()

      builder.advanceLexer()
      parseObjectEntries(insideObject = true)
      if (!pass(RBrace)) {
        builder.error("expected '}'")
      }

      marker.done(Object)
    }

    def parseObjectEntries(insideObject: Boolean) = {
      val marker = builder.mark()

      while (!matches(RBrace.orEof)) {
        if (matches(ObjectEntryStart)) {
          parseObjectEntry()
          pass(Comma)
        } else {
          tokenError("expected object field, include" + (if (insideObject) " or '}'" else ""))
        }
      }

      marker.done(ObjectEntries)
    }

    def parseObjectEntry() {
      if (matches(ValueEnding)) {
        builder.error("expected object field or include")
        return
      }

      if (matchesUnquoted("include"))
        parseInclude()
      else
        parseObjectField()
      errorUntil(ValueEnding.orNewLineOrEof, "unexpected token", onlyNonEmpty = true)
    }

    def parseInclude() = {
      val marker = builder.mark()

      builder.advanceLexer()
      parseIncluded()

      marker.done(Include)
    }

    def parseIncluded() {
      val marker = builder.mark()

      if (pass(QuotedString)) ()
      else if (IncludeQualifiers.exists(matchesUnquoted)) {
        builder.advanceLexer()
        if (pass(QuotedString)) {
          if (matchesUnquoted(")")) {
            builder.advanceLexer()
          } else errorUntil(ValueEnding.orNewLineOrEof, "expected ')'")
        } else errorUntil(ValueEnding.orNewLineOrEof, "expected quoted string")
      } else errorUntil(ValueEnding.orNewLineOrEof,
        "expected quoted string, optionally wrapped in 'url(...)', 'file(...)' or 'classpath(...)'")

      marker.done(Included)
    }

    def parseObjectField(): Unit = {
      val marker = builder.mark()

      parsePath(reference = false)
      if (matches(LBrace)) {
        parseObject()
      } else if (pass(PathValueSeparator)) {
        parseValue()
      } else errorUntil(ValueEnding.orNewLineOrEof,
        "expected ':', '=', '+=' or object")

      marker.done(ObjectField)
    }

    def parsePath(reference: Boolean): Unit = {
      val endingMatcher = if (reference) PathEnding.orNewLineOrEof else PathEnding.orEof
      val pathType = if (reference) ReferencePath else Path

      if (matches(endingMatcher)) {
        builder.error("expected path expression")
        return
      }

      var pathMarker = parseKey(pathType, None)
      while (pass(Period.noNewLine)) {
        pathMarker = parseKey(pathType, Some(pathMarker))
      }

    }

    def parseKey(pathType: HoconElementType, prefixPathMarker: Option[Marker]): Marker = {
      val pathMarker = prefixPathMarker match {
        case Some(m) => m.precede()
        case None => builder.mark()
      }

      val marker = builder.mark()

      val first = !prefixPathMarker.isDefined
      if (matches(KeyEnding) || (!first && matches(Empty.orNewLineOrEof))) {
        marker.error("expected key (use quoted \"\" if you want an empty string)")
        pathMarker.done(pathType)
        return pathMarker
      }

      def parseKeyToken() {
        if (!pass(KeyTokens)) {
          tokenError("key must be a concatenation of unquoted, quoted or multiline strings " +
            "(characters $ \" { } [ ] : = , + # ` ^ ? ! @ * & \\ are forbidden unquoted)")
        }
      }

      parseKeyToken()
      while (!matches(KeyEnding.orNewLineOrEof)) {
        parseKeyToken()
      }

      marker.done(Key)

      val leftWhitespacesBinder =
        if (first) WhitespacesBinders.DEFAULT_LEFT_BINDER else WhitespacesBinders.GREEDY_LEFT_BINDER
      val rightWhitespacesBinder =
        if (matches(Period)) WhitespacesBinders.GREEDY_RIGHT_BINDER else WhitespacesBinders.DEFAULT_RIGHT_BINDER
      marker.setCustomEdgeTokenBinders(leftWhitespacesBinder, rightWhitespacesBinder)

      pathMarker.done(pathType)

      pathMarker
    }

    def parseValue(): Unit = {
      val marker = builder.mark()

      if (matches(ValueEnding.orEof)) {
        marker.error("expected value")
        return
      }

      def tryParse(parsingCode: => Boolean, element: HoconElementType): Boolean = {
        val marker = builder.mark()
        if (parsingCode) {
          marker.done(element)
          true
        } else {
          marker.rollbackTo()
          false
        }
      }

      def passKeyword(kw: String) =
        if (matchesUnquoted(kw)) {
          builder.advanceLexer()
          true
        } else
          false

      val endingMatcher = ValueEnding.orNewLineOrEof

      def tryParseNull = tryParse(passKeyword("null") && matches(endingMatcher), Null)
      def tryParseBoolean = tryParse((passKeyword("true") || passKeyword("false")) && matches(endingMatcher), Boolean)
      def tryParseNumber = tryParse(passNumber() && matches(endingMatcher), Number)

      def parseValuePart() {
        if (matches(LBrace)) {
          parseObject()
        } else if (matches(LBracket)) {
          parseArray()
        } else if (matches(Dollar)) {
          parseReference()
        } else if (!pass(SimpleValuePart)) {
          tokenError("characters $ \" { } [ ] : = , + # ` ^ ? ! @ * & \\ are forbidden unquoted")
        }
      }

      if (!tryParseNull && !tryParseBoolean && !tryParseNumber) {
        parseValuePart()
        while (!matches(endingMatcher)) {
          parseValuePart()
        }
      }

      marker.done(Value)
    }

    def passNumber(): Boolean = matchesUnquoted(IntegerPattern) && {
      // we need to detect whitespaces between tokens forming a number to behave as if number is a single token
      val integerRawTokenIdx = builder.rawTokenIndex
      builder.advanceLexer()

      val gotPeriod = matches(Period)
      val noPeriodWhitespace = gotPeriod && builder.rawTokenIndex == integerRawTokenIdx + 1

      if (gotPeriod) {
        builder.advanceLexer()
      }

      val gotDecimalPart = gotPeriod && matchesUnquoted(DecimalPartPattern)
      val noDecimalPartWhitespace = gotDecimalPart && builder.rawTokenIndex() == integerRawTokenIdx + 2

      if (gotDecimalPart) {
        builder.advanceLexer()
      }

      (!gotPeriod || noPeriodWhitespace) && (!gotDecimalPart || noDecimalPartWhitespace)
    }

    def parseArray() {
      val marker = builder.mark()
      builder.advanceLexer()

      while (!matches(ArrayElementsEnding.orEof)) {
        if (matches(ValueStart)) {
          parseValue()
          pass(Comma)
        } else {
          tokenError("expected array element or ']'")
        }
      }

      if (!pass(RBracket)) {
        builder.error("expected ']'")
      }

      marker.done(Array)
    }

    def parseReference() {
      val marker = builder.mark()
      builder.advanceLexer()
      builder.advanceLexer()
      pass(QMark)
      parsePath(reference = true)
      if (!pass(RefRBrace)) {
        builder.error("expected '}'")
      }
      marker.done(Reference)
    }

  }

}
