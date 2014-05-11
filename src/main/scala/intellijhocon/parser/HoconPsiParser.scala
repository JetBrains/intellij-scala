package intellijhocon.parser

import com.intellij.lang.{WhitespacesAndCommentsBinder, WhitespacesBinders, PsiBuilder, PsiParser}
import com.intellij.psi.tree.IElementType
import scala.util.matching.Regex
import com.intellij.lang.PsiBuilder.Marker
import java.{util => ju, lang => jl}
import com.intellij.lang.WhitespacesAndCommentsBinder.TokenTextGetter
import intellijhocon.lexer.{HoconTokenType, HoconTokenSets}
import intellijhocon.Util
import scala.Some

class HoconPsiParser extends PsiParser {

  import Util._
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

    object DocumentationCommentsBinder extends WhitespacesAndCommentsBinder {
      override def getEdgePosition(tokens: ju.List[IElementType], atStreamEdge: Boolean, getter: TokenTextGetter) = {

        def docCommentsStart(acc: Int, i: Int): Int = {
          lazy val token = tokens.get(i)
          lazy val whitespaceAfterHashComment = i > 0 && tokens.get(i - 1) == HashComment &&
            token == LineBreakingWhitespace && getter.get(i).charIterator.count(_ == '\n') <= 1

          if (i >= tokens.size || !WhitespaceOrComment.contains(token)) acc
          else if (token == HashComment || whitespaceAfterHashComment) docCommentsStart(acc, i + 1)
          else docCommentsStart(i + 1, i + 1)
        }

        docCommentsStart(0, 0)
      }
    }

    // beware of rollbacks!
    var newLineSuppressedIndex: Int = 0

    def newLineBeforeCurrentToken =
      builder.rawTokenIndex > newLineSuppressedIndex && builder.rawLookup(-1) == LineBreakingWhitespace

    def suppressNewLine() {
      newLineSuppressedIndex = builder.rawTokenIndex
    }

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

    def setWhitespacesAndTokenBinders(marker: Marker, nonGreedyLeft: Boolean, nonGreedyRight: Boolean) {
      import WhitespacesBinders._
      marker.setCustomEdgeTokenBinders(
        if (nonGreedyLeft) DEFAULT_LEFT_BINDER else GREEDY_LEFT_BINDER,
        if (nonGreedyRight) DEFAULT_RIGHT_BINDER else GREEDY_RIGHT_BINDER)
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
          tokenError("expected object field" + (if (insideObject) ", include or '}'" else " or include"))
        }
      }

      marker.done(ObjectEntries)
      marker.setCustomEdgeTokenBinders(DocumentationCommentsBinder, WhitespacesBinders.DEFAULT_RIGHT_BINDER)
    }

    def parseObjectEntry() {
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

      parsePath(Path)
      if (matches(LBrace)) {
        parseObject()
      } else if (pass(PathValueSeparator)) {
        if (matches(ValueStart)) {
          parseValue()
        } else {
          errorUntil(ValueEnding.orNewLineOrEof, "expected value for object field")
        }
      } else errorUntil(ValueEnding.orNewLineOrEof,
        "expected ':', '=', '+=' or object")

      marker.done(ObjectField)

      marker.setCustomEdgeTokenBinders(DocumentationCommentsBinder, WhitespacesBinders.DEFAULT_RIGHT_BINDER)
    }

    def parsePath(pathType: HoconElementType, prefixMarker: Option[Marker] = None): Unit = {
      val first = !prefixMarker.isDefined
      if (first) {
        suppressNewLine()
      }

      if (!matches(PathEnding.orNewLineOrEof)) {
        if (!first) {
          pass(Period.noNewLine)
        }
        val marker = prefixMarker.map(_.precede()).getOrElse(builder.mark())
        if (!matches(KeyEnding.orNewLineOrEof)) {
          parseKey(first)
        } else {
          builder.error("expected key (use quoted \"\" if you want empty key)")
        }
        marker.done(pathType)
        parsePath(pathType, Some(marker))
      }
    }

    def parseKey(first: Boolean): Unit = {
      val marker = builder.mark()

      def parseKeyParts(first: Boolean) {
        if (!matches(KeyEnding.orNewLineOrEof)) {
          if (matches(UnquotedChars)) {
            parseAsUnquotedString(UnquotedChars.noNewLine, first, PathEnding.orNewLineOrEof)
          } else if (matches(StringLiteral)) {
            builder.advanceLexer()
          } else {
            tokenError("key must be a concatenation of unquoted, quoted or multiline strings " +
              "(characters $ \" { } [ ] : = , + # ` ^ ? ! @ * & \\ are forbidden unquoted)")
          }
          parseKeyParts(first = false)
        }
      }

      suppressNewLine()
      parseKeyParts(first)

      marker.done(Key)

      setWhitespacesAndTokenBinders(marker, first, matches(PathEnding.orNewLineOrEof))
    }

    def parseAsUnquotedString(matcher: Matcher, nonGreedyLeft: Boolean, nonGreedyRightMatcher: Matcher): Unit = {
      val marker = builder.mark()
      suppressNewLine()
      while (matches(matcher)) {
        builder.advanceLexer()
      }
      marker.done(UnquotedString)

      setWhitespacesAndTokenBinders(marker, nonGreedyLeft, matches(nonGreedyRightMatcher))
    }

    def parseValue(): Unit = {
      val marker = builder.mark()

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

      def parseValueParts(first: Boolean) {
        if (!matches(endingMatcher)) {
          if (matches(LBrace)) {
            parseObject()
          } else if (matches(LBracket)) {
            parseArray()
          } else if (matches(Dollar)) {
            parseReference()
          } else if (matches(ValueUnquotedChars)) {
            parseAsUnquotedString(ValueUnquotedChars.noNewLine, first, ValueEnding.orNewLineOrEof)
          } else if (matches(StringLiteral)) {
            builder.advanceLexer()
          } else {
            tokenError("characters $ \" { } [ ] : = , + # ` ^ ? ! @ * & \\ are forbidden unquoted")
          }
          parseValueParts(first = false)
        }
      }

      suppressNewLine()
      if (!tryParseNull && !tryParseBoolean && !tryParseNumber) {
        parseValueParts(first = true)
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
      if (matches(ReferencePathStart.noNewLine)) {
        parsePath(ReferencePath)
        if (!pass(RefRBrace)) {
          builder.error("expected '}'")
        }
      } else errorUntil(PathEnding.orNewLineOrEof, "expected path expression")
      pass(RefRBrace)

      marker.done(Reference)
    }

  }

}
