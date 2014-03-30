package intellijhocon

import com.intellij.lexer.LexerBase
import scala.util.matching.Regex
import com.intellij.psi.TokenType
import com.intellij.psi.tree.IElementType

class HoconLexer extends LexerBase {
  type State = Int

  import HoconTokenType._

  val Initial = 0
  val YieldNewlines = 1
  val YieldWhitespace = 2
  val StartingReference = 3
  val Reference = 4

  private implicit class CharSequenceOps(cs: CharSequence) {
    def startsWith(str: String) =
      cs.length >= str.length && str.contentEquals(cs.subSequence(0, str.length))
  }

  trait TokenMatcher {
    def matchToken(seq: CharSequence, state: State): Option[(Int, IElementType, State)]
  }

  case class LiteralTokenMatcher(str: String, token: IElementType, transition: State => State) extends TokenMatcher {
    def matchToken(seq: CharSequence, state: State) =
      if (seq.startsWith(str))
        Some((str.length, token, transition(state)))
      else None
  }

  case class RegexTokenMatcher(regex: Regex, token: IElementType, transition: State => State) extends TokenMatcher {
    def matchToken(seq: CharSequence, state: State) =
      regex.findPrefixMatchOf(seq).map(m => (m.end, token, transition(state)))
  }

  private def afterSimpleValue(state: State) = state match {
    case Initial | YieldNewlines | YieldWhitespace => YieldWhitespace
    case StartingReference | Reference => Reference
  }

  val matchers = List(
    WhitespaceMatcher,
    LiteralTokenMatcher("{", LBrace, _ => Initial),
    RBraceMatcher,
    LiteralTokenMatcher("[", LSquare, _ => Initial),
    LiteralTokenMatcher("]", RSquare, _ => YieldNewlines),
    LiteralTokenMatcher(":", Colon, _ => Initial),
    LiteralTokenMatcher(",", Comma, _ => Initial),
    LiteralTokenMatcher("=", Equals, _ => Initial),
    LiteralTokenMatcher("+=", PlusEquals, _ => Initial),
    RegexTokenMatcher( """\$\{(\?)?""".r, RefStart, _ => StartingReference),
    LiteralTokenMatcher("\n", NewLine, _ => Initial),
    LiteralTokenMatcher("null", Null, afterSimpleValue),
    LiteralTokenMatcher("true", True, afterSimpleValue),
    LiteralTokenMatcher("false", False, afterSimpleValue),
    RegexTokenMatcher( """(//|#)[^\n]*""".r, Comment, identity),
    RegexTokenMatcher( """-?(0|[1-9][0-9]*)(\.[0-9]*)?((e|E)(\+|-)?[0-9]+)?""".r, Number, afterSimpleValue),
    UnquotedStringMatcher,
    RegexTokenMatcher("\"{3}([^\"]|\"{1,2}[^\"])*\"{3,}".r, MultilineString, afterSimpleValue),
    RegexTokenMatcher( """"([^"\\]|\\(["\\bfnrt]|u[0-9A-Fa-f]{4}))*"""".r, QuotedString, afterSimpleValue),
    RegexTokenMatcher(".".r, TokenType.BAD_CHARACTER, identity)
  )

  object RBraceMatcher extends TokenMatcher {
    def matchToken(seq: CharSequence, state: State) =
      if (seq.length() >= 1 && seq.charAt(0) == '}') state match {
        case Reference => Some((1, RefEnd, YieldWhitespace))
        case _ => Some((1, RBrace, YieldNewlines))
      } else None
  }

  private val forbidden = """$"{}[]:=,+#`^?!@*&\"""
  private val specialWhitespace = "\u00A0\u2007\u202F\uFEFF"

  def cancelsWhitespace(seq: CharSequence, index: Int) =
    index >= seq.length || "{}[]+=:,\n#".contains(seq.charAt(index)) || isCStyleComment(seq, index)

  def isHoconWhitespace(char: Char) = char.isWhitespace || specialWhitespace.contains(char)

  def isCStyleComment(seq: CharSequence, index: Int) =
    seq.subSequence(index, seq.length).startsWith("//")

  def continuesUnquotedString(seq: CharSequence, index: Int) = index < seq.length && {
    val char = seq.charAt(index)
    !forbidden.contains(char) && !isHoconWhitespace(char) && !isCStyleComment(seq, index)
  }

  object UnquotedStringMatcher extends TokenMatcher {
    def matchToken(seq: CharSequence, state: State) =
      if (seq.length > 0 && !seq.charAt(0).isDigit && seq.charAt(0) != '-') {
        var c = 0
        while (continuesUnquotedString(seq, c)) {
          c += 1
        }
        if (c > 0) Some((c, UnquotedString, afterSimpleValue(state))) else None
      } else None
  }

  object WhitespaceMatcher extends TokenMatcher {
    def matchToken(seq: CharSequence, state: State) = {
      var c = 0
      val acceptNewlines = state == Initial
      def char = seq.charAt(c)
      while (c < seq.length && (acceptNewlines || char != '\n') && isHoconWhitespace(char)) {
        c += 1
      }
      if (c > 0) {
        if ((state == YieldWhitespace || state == Reference) && !cancelsWhitespace(seq, c))
          Some((c, WhitespaceString, state))
        else
          Some((c, TokenType.WHITE_SPACE, state))
      } else None
    }
  }

  private var input: CharSequence = _
  private var endOffset: Int = _
  private var state: State = Initial

  private var tokenStart: Int = _
  private var tokenEnd: Int = _
  private var token: IElementType = _

  def getBufferEnd = endOffset

  def getBufferSequence = input

  def advance() = {
    tokenStart = tokenEnd
    val seq = input.subSequence(tokenStart, endOffset)
    if (seq.length > 0) {

      def findMatch(matchers: List[TokenMatcher]): (Int, IElementType, State) =
        matchers.head.matchToken(seq, state) match {
          case Some(result) => result
          case _ => findMatch(matchers.tail)
        }

      val (length, newToken, newState) = findMatch(matchers)

      tokenEnd = tokenStart + length
      token = newToken
      state = newState
    } else {
      state = Initial
      token = null
    }
  }

  def getTokenEnd = tokenEnd

  def getTokenStart = tokenStart

  def getTokenType = {
    if (token == null) {
      advance()
    }
    token
  }

  def getState = state

  def start(buffer: CharSequence, startOffset: Int, endOffset: Int, initialState: Int) = {
    this.input = buffer
    this.tokenStart = startOffset
    this.tokenEnd = startOffset
    this.endOffset = endOffset
    this.state = initialState
  }
}
