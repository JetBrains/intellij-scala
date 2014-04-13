package intellijhocon

import com.intellij.lexer.LexerBase
import scala.util.matching.Regex
import com.intellij.psi.TokenType
import com.intellij.psi.tree.IElementType

class HoconLexer extends LexerBase {
  type State = Int

  import HoconTokenType._

  val Initial = 0
  val SimpleValue = 1
  val Reference = 1 << 2
  val Number = 1 << 3

  implicit class CharSequenceOps(cs: CharSequence) {
    def startsWith(str: String) =
      cs.length >= str.length && str.contentEquals(cs.subSequence(0, str.length))
  }

  trait TokenMatcher {
    def matchToken(seq: CharSequence, state: State): Option[Int]

    def token: IElementType

    def transition: State => State
  }

  case class LiteralTokenMatcher(str: String,
                                 token: IElementType,
                                 condition: State => Boolean = _ => true,
                                 transition: State => State = identity)
    extends TokenMatcher {

    def matchToken(seq: CharSequence, state: State) =
      if (condition(state) && seq.startsWith(str))
        Some(str.length)
      else None
  }

  case class RegexTokenMatcher(regex: Regex,
                               token: IElementType,
                               condition: State => Boolean = _ => true,
                               transition: State => State = identity)
    extends TokenMatcher {

    def matchToken(seq: CharSequence, state: State) =
      if (condition(state))
        regex.findPrefixMatchOf(seq).map(_.end)
      else None
  }

  def requireState(states: State*): State => Boolean =
    states.contains

  def hasFlag(flag: State): State => Boolean =
    state => (state & flag) > 0

  def doesntHaveFlag(flag: State): State => Boolean =
    state => (state & flag) == 0

  def forceState(state: State): State => State =
    _ => state

  def modify(add: State = 0, remove: State = 0): State => State =
    state => (state | add) & ~remove

  def always: State => Boolean =
    _ => true

  def simpleValueIfInitial(state: State) =
    if (state == Initial) SimpleValue else state

  val matchers = List(
    WhitespaceMatcher,
    LiteralTokenMatcher("{", LBrace, doesntHaveFlag(Reference), forceState(Initial)),
    RegexTokenMatcher( """\$\{(\?)?""".r, RefStart, always, forceState(Reference)),
    LiteralTokenMatcher("}", RefEnd, hasFlag(Reference), forceState(SimpleValue)),
    LiteralTokenMatcher("}", RBrace, always, forceState(SimpleValue)),
    LiteralTokenMatcher("[", LBracket, doesntHaveFlag(Reference), forceState(Initial)),
    LiteralTokenMatcher("]", RBracket, always, forceState(SimpleValue)),
    LiteralTokenMatcher(":", Colon, hasFlag(SimpleValue), forceState(Initial)),
    LiteralTokenMatcher(",", Comma, hasFlag(SimpleValue), forceState(Initial)),
    LiteralTokenMatcher("=", Equals, hasFlag(SimpleValue), forceState(Initial)),
    LiteralTokenMatcher("+=", PlusEquals, hasFlag(SimpleValue), forceState(Initial)),
    LiteralTokenMatcher(".", Dot, always, s => simpleValueIfInitial(s)),
    LiteralTokenMatcher("\n", NewLine, hasFlag(SimpleValue), forceState(Initial)),
    RegexTokenMatcher( """(//|#)[^\n]*""".r, Comment, always, identity),
    RegexTokenMatcher( """([0-9]+)((e|E)(\+|-)?[0-9]+)?""".r, Decimal, hasFlag(Number), s => simpleValueIfInitial(s) & ~Number),
    RegexTokenMatcher( """-?(0|[1-9][0-9]*)""".r, Integer, always, s => simpleValueIfInitial(s) | Number),
    UnquotedCharsMatcher,
    RegexTokenMatcher("\"{3}([^\"]|\"{1,2}[^\"])*\"{3,}".r, MultilineString, always, s => simpleValueIfInitial(s) & ~Number),
    RegexTokenMatcher( """"([^"\\]|\\(["\\bfnrt]|u[0-9A-Fa-f]{4}))*"""".r, QuotedString, always, s => simpleValueIfInitial(s) & ~Number),
    RegexTokenMatcher(".".r, TokenType.BAD_CHARACTER, always, modify(remove = Number))
  )

  val stateTransitions = matchers.map(m => (m.token, m.transition)).toMap

  val forbidden = """$"{}[]:=,+#`^?!@*&\"""
  val specialWhitespace = "\u00A0\u2007\u202F\uFEFF"

  def isHoconWhitespace(char: Char) = char.isWhitespace || specialWhitespace.contains(char)

  def isCStyleComment(seq: CharSequence, index: Int) =
    seq.subSequence(index, seq.length).startsWith("//")

  def continuesUnquotedChars(seq: CharSequence, index: Int) = index < seq.length && {
    val char = seq.charAt(index)
    char != '.' && !forbidden.contains(char) && !isHoconWhitespace(char) && !isCStyleComment(seq, index)
  }

  object UnquotedCharsMatcher extends TokenMatcher {
    def matchToken(seq: CharSequence, state: State) =
      if (seq.length > 0 && !seq.charAt(0).isDigit && seq.charAt(0) != '-') {
        var c = 0
        while (continuesUnquotedChars(seq, c)) {
          c += 1
        }
        if (c > 0) Some(c) else None
      } else None


    def token = UnquotedChars

    def transition =
      state => simpleValueIfInitial(state) & ~Number
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
        Some(c)
      } else None
    }

    def token = TokenType.WHITE_SPACE

    def transition =
      modify(remove = Number)
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

      val newState = if (token != null) stateTransitions(token)(state) else state

      def findMatch(matchers: List[TokenMatcher]): (Int, IElementType) = {
        val head :: tail = matchers
        head.matchToken(seq, newState) match {
          case Some(result) => (result, head.token)
          case _ => findMatch(tail)
        }
      }

      val (length, newToken) = findMatch(matchers)

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
    this.token = null
    this.input = buffer
    this.tokenStart = startOffset
    this.tokenEnd = startOffset
    this.endOffset = endOffset
    this.state = initialState
  }
}
