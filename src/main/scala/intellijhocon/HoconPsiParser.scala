package intellijhocon

import com.intellij.lang.{PsiBuilder, PsiParser}
import com.intellij.psi.tree.IElementType

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

  implicit def unquotedTokenMatcher(unquoted: String) = new TokenMatcher {
    def matches(builder: PsiBuilder) =
      builder.getTokenType == UnquotedChars && builder.getTokenText == unquoted
  }

  object Eof extends TokenMatcher {
    def matches(builder: PsiBuilder) = builder.eof
  }

  val ObjectEntriesEnding = RBrace | Eof
  val ObjectEntryEnding = Comma | NewLine | ObjectEntriesEnding
  val PathEnding = Colon | Equals | PlusEquals | LBrace | ObjectEntryEnding
  val PathElementEnding = Period | PathEnding

  val PathElementToken = UnquotedChars | QuotedString | MultilineString

  val ValueSeparator = NewLine | Comma
  val PathValueSeparator = Colon | Equals | PlusEquals

  class Parser(builder: PsiBuilder) {
    def matches(matcher: TokenMatcher) =
      matcher.matches(builder)

    def matchesAhead(matcher: TokenMatcher, steps: Int) = {
      val mark = builder.mark()
      for (_ <- 0 to steps) advance()
      val result = matcher.matches(builder)
      mark.rollbackTo()
      result
    }

    def advance() =
      builder.advanceLexer()

    def error(msg: String) =
      builder.error(msg)

    def defaultFailure =
      throw new FatalParseException

    def expect(matcher: TokenMatcher, onFailure: => Unit = defaultFailure): Boolean = {
      val result = matcher.matches(builder)
      if (result)
        advance()
      else
        onFailure
      result
    }

    def errorUntil(matcher: TokenMatcher, msg: String, onlyNonEmpty: Boolean = false) {
      val mark = builder.mark
      while (!builder.eof && !matches(matcher)) {
        advance()
      }
      mark.error(msg)
    }

    def proceedTo(matcher: TokenMatcher, errorMsg: String) {
      if (!matches(matcher)) {
        val mark = builder.mark()
        while (!builder.eof && !matches(matcher)) {
          advance()
        }
        mark.error(errorMsg)
      }
    }

    def tokenError(msg: String) {
      val mark = builder.mark()
      advance()
      mark.error(msg)
    }

    def pass(matcher: TokenMatcher) =
      expect(matcher, onFailure = ())

    def emit(elementType: HoconElementType)(code: => Unit) = {
      val mark = builder.mark()
      try code finally mark.done(elementType)
    }

    def parseFile() {
      if (matches(LBrace))
        parseObject()
      else
        parseObjectEntries()
      proceedTo(Eof, "expected end of file here")
    }

    def parseObject() = emit(Object) {
      expect(LBrace)
      parseObjectEntries()
      if (!pass(RBrace)) {
        error("expected } here")
      }
    }

    def parseObjectEntries() = emit(ObjectEntries) {
      while (!matches(ObjectEntriesEnding)) {
        parseObjectEntry()
        pass(ValueSeparator)
      }
    }

    def parseObjectEntry() {
      if (matches(ObjectEntryEnding)) {
        error("expected object field or include clause here")
        return
      }

      if (matches("include"))
        parseInclude()
      else
        parseObjectField()
      proceedTo(ObjectEntryEnding, "expected newline, comma or } here")
    }

    def parseInclude() = emit(Include) {
      expect("include")
      pass(NewLine)
      parseIncluded()
    }

    def parseIncluded(): Unit = emit(Included) {
      if (pass(QuotedString)) ()
      else if (pass("url(" | "file(" | "classpath(")) {
        if (!pass(QuotedString)) {
          errorUntil(ObjectEntryEnding, "expected quoted string here")
          return
        }
        if (!pass(")")) {
          errorUntil(ObjectEntryEnding, "expected ) here")
          return
        }
      } else errorUntil(ObjectEntryEnding,
        "expected quoted string, optionally wrapped in url(), file() or classpath()")
    }

    def parseObjectField(): Unit = emit(ObjectField) {
      parsePath()
      if (matches(LBrace)) {
        parseObject()
      } else if (pass(PathValueSeparator)) {
        parseValue()
      } else errorUntil(ObjectEntryEnding,
        "expected : or = or += or object here")
    }

    def parsePath(): Unit = emit(Path) {
      if (matches(PathEnding)) {
        error("expected path here")
        return
      }

      parsePathElement()
      while (pass(Period)) {
        parsePathElement()
      }
    }

    def parseValue(): Unit = emit(Value) {
      if (matches(ObjectEntryEnding)) {
        error("expected value here")
      }

      while (!matches(ObjectEntryEnding)) {
        advance()
      }
    }

    def parsePathElement(): Unit = emit(PathElement) {
      if (matches(PathElementEnding)) {
        error("path has a leading, trailing or two adjacent period '.' (use quoted \"\" empty string if you want an empty element)")
        return
      }

      while (!matches(PathElementEnding)) {
        expect(PathElementToken, onFailure = tokenError("expected unquoted, quoted or multiline string here"))
      }
    }
  }

}
