package intellijhocon

import com.intellij.lang.{WhitespaceSkippedCallback, WhitespacesBinders, PsiBuilder, PsiParser}
import com.intellij.psi.tree.IElementType
import scala.util.matching.Regex

class HoconPsiParser extends PsiParser {

  import HoconTokenType._
  import HoconElementType._

  def parse(root: IElementType, builder: PsiBuilder) = {
    val file = builder.mark()
    new Parser(builder).parseFile()
    file.done(root)
    builder.getTreeBuilt
  }

  class FatalParseException extends RuntimeException

  trait TokenMatcher {
    self =>

    def matches(builder: PsiBuilder): Boolean

    def |(otherMatcher: TokenMatcher) = new TokenMatcher {
      def matches(builder: PsiBuilder) =
        self.matches(builder) || otherMatcher.matches(builder)
    }
  }

  implicit def singleTokenMatcher(tokenType: HoconTokenType) = new TokenMatcher {
    def matches(builder: PsiBuilder) =
      builder.getTokenType == tokenType
  }

  implicit def unquotedTokenMatcherFromString(unquoted: String) = new TokenMatcher {
    def matches(builder: PsiBuilder) =
      builder.getTokenType == UnquotedChars && builder.getTokenText == unquoted
  }

  implicit def unquotedTokenMatcherFromRegex(regex: Regex) = new TokenMatcher {
    def matches(builder: PsiBuilder) =
      builder.getTokenType == UnquotedChars && regex.pattern.matcher(builder.getTokenText).matches
  }

  object Eof extends TokenMatcher {
    def matches(builder: PsiBuilder) = builder.eof
  }

  val ObjectEntriesEnding = RBrace | Eof
  val ObjectEntryEnding = Comma | NewLine | ObjectEntriesEnding
  val ArrayValuesEnding = RBracket | RBrace | Eof
  val ArrayValueEnding = Comma | NewLine | ArrayValuesEnding
  val EntryPathEnding = Colon | Equals | PlusEquals | LBrace | ObjectEntryEnding
  val ReferencePathEnding = NewLine | RefRBrace | Eof

  val PathElementToken = UnquotedChars | QuotedString | MultilineString

  val ValueSeparator = NewLine | Comma
  val PathValueSeparator = Colon | Equals | PlusEquals
  val SimpleValueElement = UnquotedChars | Period | QuotedString | MultilineString

  val IntegerPattern = """-?(0|[1-9][0-9]*)""".r
  val DecimalPartPattern = """([0-9]+)((e|E)(\+|-)?[0-9]+)?""".r

  class Parser(builder: PsiBuilder) {

    import builder._

    def withWhitespaceSkippedCallback[T](callback: WhitespaceSkippedCallback)(code: => T): T = {
      builder.setWhitespaceSkippedCallback(callback)
      try code finally {
        builder.setWhitespaceSkippedCallback(null)
      }
    }

    def matches(matcher: TokenMatcher) =
      matcher.matches(builder)

    def matchesAhead(matcher: TokenMatcher, steps: Int) = {
      val marker = mark()
      for (_ <- 0 to steps) advanceLexer()
      val result = matcher.matches(builder)
      marker.rollbackTo()
      result
    }

    def defaultFailure =
      throw new FatalParseException

    def expect(matcher: TokenMatcher, onFailure: => Unit = defaultFailure): Boolean = {
      val result = matcher.matches(builder)
      if (result)
        advanceLexer()
      else
        onFailure
      result
    }

    def errorUntil(matcher: TokenMatcher, msg: String, onlyNonEmpty: Boolean = false) {
      val marker = mark()
      while (!builder.eof && !matches(matcher)) {
        advanceLexer()
      }
      marker.error(msg)
    }

    def errorWhileNot(matcher: TokenMatcher, errorMsg: String) {
      if (!matches(matcher)) {
        val marker = mark()
        while (!builder.eof && !matches(matcher)) {
          advanceLexer()
        }
        marker.error(errorMsg)
      }
    }

    def tokenError(msg: String) {
      val marker = mark()
      advanceLexer()
      marker.error(msg)
    }

    def pass(matcher: TokenMatcher) =
      expect(matcher, onFailure = ())

    def parseFile() {
      if (matches(LBrace))
        parseObject()
      else
        parseObjectEntries()
      errorWhileNot(Eof, "expected end of file")
    }

    def parseObject() = {
      val marker = mark()

      expect(LBrace)
      parseObjectEntries()
      if (!pass(RBrace)) {
        error("expected '}'")
      }

      marker.done(Object)
    }

    def parseObjectEntries() = {
      val marker = mark()

      while (!matches(ObjectEntriesEnding)) {
        parseObjectEntry()
        pass(ValueSeparator)
      }

      marker.done(ObjectEntries)
    }

    def parseObjectEntry() {
      if (matches(ObjectEntryEnding)) {
        error("expected object field or include")
        return
      }

      if (matches("include"))
        parseInclude()
      else
        parseObjectField()
      errorWhileNot(ObjectEntryEnding, "expected new line, ',' or '}'")
    }

    def parseInclude() = {
      val marker = mark()

      expect("include")
      pass(NewLine)
      parseIncluded()

      marker.done(Include)
    }

    def parseIncluded() {
      val marker = mark()

      if (pass(QuotedString)) ()
      else if (pass("url(" | "file(" | "classpath(")) {
        if (!pass(QuotedString)) {
          errorUntil(ObjectEntryEnding, "expected quoted string")
        } else if (!pass(")")) {
          errorUntil(ObjectEntryEnding, "expected ')'")
        }
      } else errorUntil(ObjectEntryEnding,
        "expected quoted string, optionally wrapped in 'url(...)', 'file(...)' or 'classpath(...)'")

      marker.done(Included)
    }

    def parseObjectField(): Unit = {
      val marker = mark()

      parsePath(EntryPathEnding)
      if (matches(LBrace)) {
        parseObject()
      } else if (pass(PathValueSeparator)) {
        parseValue(ObjectEntryEnding)
      } else errorUntil(ObjectEntryEnding,
        "expected ':', '=', '+=' or object")

      marker.done(ObjectField)
    }

    def parsePath(endingMatcher: TokenMatcher): Unit = {
      val marker = mark()

      if (matches(endingMatcher)) {
        marker.error("expected path expression")
        return
      }

      val elementEndingMatcher = endingMatcher | Period
      parsePathElement(elementEndingMatcher, first = true)
      while (pass(Period)) {
        parsePathElement(elementEndingMatcher, first = false)
      }

      marker.done(Path)
    }

    def parsePathElement(endingMatcher: TokenMatcher, first: Boolean): Unit = {
      val marker = mark()

      if (matches(endingMatcher)) {
        marker.error("expected path element (use quoted \"\" if you want an empty string)")
        return
      }

      while (!matches(endingMatcher)) {
        expect(PathElementToken, onFailure =
          tokenError("path element must be a concatenation of unquoted, quoted or multiline strings " +
            "(characters $\"{}[]:=,+#`^?!@*&\\ are forbidden in an unquoted string)"))
      }

      marker.done(PathElement)

      val leftWhitespacesBinder =
        if (first) WhitespacesBinders.DEFAULT_LEFT_BINDER else WhitespacesBinders.GREEDY_LEFT_BINDER
      val rightWhitespacesBinder =
        if (matches(Period)) WhitespacesBinders.GREEDY_RIGHT_BINDER else WhitespacesBinders.DEFAULT_RIGHT_BINDER
      marker.setCustomEdgeTokenBinders(leftWhitespacesBinder, rightWhitespacesBinder)
    }

    def parseValue(endingMatcher: TokenMatcher): Unit = {
      val marker = mark()

      if (matches(endingMatcher)) {
        marker.error("expected value")
        return
      }

      def tryParse(parsingCode: => Boolean, element: HoconElementType): Boolean = {
        val marker = mark()
        if (parsingCode) {
          marker.done(element)
          true
        } else {
          marker.rollbackTo()
          false
        }
      }

      def tryParseNull = tryParse(pass("null") && matches(endingMatcher), Null)
      def tryParseBoolean = tryParse(pass("true" | "false") && matches(endingMatcher), Boolean)
      def tryParseNumber = tryParse(passNumber() && matches(endingMatcher), Number)

      if (!tryParseNull && !tryParseBoolean && !tryParseNumber) {
        while (!matches(endingMatcher)) {
          if (matches(LBrace)) {
            parseObject()
          } else if (matches(LBracket)) {
            parseArray()
          } else if (matches(Dollar)) {
            parseReference()
          } else if (!pass(SimpleValueElement)) {
            tokenError("characters $\"{}[]:=,+#`^?!@*&\\ are forbidden in an unquoted string")
          }
        }
      }

      marker.done(Value)
    }

    def passNumber(): Boolean = matches(IntegerPattern) && {
      // we need to detect whitespaces between tokens forming a number to behave as if number is a single token
      val integerRawTokenIdx = builder.rawTokenIndex
      advanceLexer()

      val gotPeriod = matches(Period)
      val noPeriodWhitespace = gotPeriod && builder.rawTokenIndex == integerRawTokenIdx + 1

      if (gotPeriod) {
        advanceLexer()
      }

      val gotDecimalPart = gotPeriod && matches(DecimalPartPattern)
      val noDecimalPartWhitespace = gotDecimalPart && builder.rawTokenIndex() == integerRawTokenIdx + 2

      if (gotDecimalPart) {
        advanceLexer()
      }

      (!gotPeriod || noPeriodWhitespace) && (!gotDecimalPart || noDecimalPartWhitespace)
    }

    def parseArray() {
      val marker = mark()
      expect(LBracket)

      while (!matches(ArrayValuesEnding)) {
        parseValue(ArrayValueEnding)
        pass(ValueSeparator)
      }

      if (!pass(RBracket)) {
        error("expected ']'")
      }

      marker.done(Array)
    }

    def parseReference() {
      val marker = mark()
      expect(Dollar)
      expect(RefLBrace)
      pass(QMark)
      parsePath(ReferencePathEnding)
      if (!pass(RefRBrace)) {
        error("expected '}'")
      }
      marker.done(Reference)
    }

  }

}
