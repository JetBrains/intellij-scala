package org.jetbrains.plugins.hocon.lexer

import com.intellij.lexer.LexerBase
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.hocon.HoconConstants

import scala.annotation.tailrec
import scala.util.matching.Regex

object HoconLexer {

  case class State(raw: Int) extends AnyVal

  val Initial = State(0)
  val Value = State(1)
  val SubStarting = State(2)
  val SubStarted = State(3)
  val Substitution = State(4)

  val States = Array(Initial, Value, SubStarting, SubStarted, Substitution)

  val ForbiddenChars = """$"{}[]:=,+#`^?!@*&\""".toSet
  val ForbiddenCharsAndDot = ForbiddenChars + '.'
  val SpecialWhitespace = "\u00A0\u2007\u202F\uFEFF"
}

class HoconLexer extends LexerBase {

  import org.jetbrains.plugins.hocon.CommonUtil._
  import org.jetbrains.plugins.hocon.lexer.HoconLexer._
  import org.jetbrains.plugins.hocon.lexer.HoconTokenType._

  case class TokenMatch(token: HoconTokenType, length: Int, newState: State)

  abstract class TokenMatcher {
    def matchToken(seq: CharSequence, state: State): Option[TokenMatch]
  }

  class LiteralTokenMatcher(str: String,
                            token: HoconTokenType,
                            condition: State => Boolean = _ => true,
                            transitionFun: State => State = identity)
    extends TokenMatcher {

    def matchToken(seq: CharSequence, state: State): Option[TokenMatch] =
      if (condition(state) && seq.startsWith(str))
        Some(TokenMatch(token, str.length, transitionFun(state)))
      else None
  }

  class RegexTokenMatcher(regex: Regex,
                          token: HoconTokenType,
                          condition: State => Boolean = _ => true,
                          transitionFun: State => State = identity)
    extends TokenMatcher {

    def matchToken(seq: CharSequence, state: State): Option[TokenMatch] =
      if (condition(state))
        regex.findPrefixMatchOf(seq).map(m => TokenMatch(token, m.end, transitionFun(state)))
      else None
  }

  def forceState(state: State): State => State =
    _ => state

  def always: State => Boolean =
    _ => true

  def onContents(state: State): State = state match {
    case Initial | SubStarting => Value
    case SubStarted => Substitution
    case _ => state
  }

  def onDollar(state: State): State = state match {
    case Initial | Value => SubStarting
    case SubStarted => Substitution
    case _ => state
  }

  def isAnyOf(states: State*): State => Boolean =
    states.contains

  def isNoneOf(states: State*): State => Boolean =
    !states.contains(_)

  val notSubstitution = isAnyOf(Initial, Value)

  val matchers = List(
    WhitespaceMatcher,
    new RegexTokenMatcher( """\$""".r, Dollar, always, onDollar),
    new LiteralTokenMatcher("{", SubLBrace, isAnyOf(SubStarting), forceState(SubStarted)),
    new LiteralTokenMatcher("?", QMark, isAnyOf(SubStarted), forceState(Substitution)),
    new LiteralTokenMatcher("}", SubRBrace, isAnyOf(SubStarted, Substitution), forceState(Value)),
    new LiteralTokenMatcher("{", LBrace, always, forceState(Initial)),
    new LiteralTokenMatcher("}", RBrace, always, forceState(Value)),
    new LiteralTokenMatcher("[", LBracket, always, forceState(Initial)),
    new LiteralTokenMatcher("]", RBracket, always, forceState(Value)),
    new LiteralTokenMatcher(":", Colon, always, forceState(Initial)),
    new LiteralTokenMatcher(",", Comma, always, forceState(Initial)),
    new LiteralTokenMatcher("=", Equals, always, forceState(Initial)),
    new LiteralTokenMatcher("+=", PlusEquals, always, forceState(Initial)),
    new LiteralTokenMatcher(".", Period, always, onContents),
    new RegexTokenMatcher( """#[^\n]*""".r, HashComment, always, identity),
    new RegexTokenMatcher( """//[^\n]*""".r, DoubleSlashComment, always, identity),
    UnquotedCharsMatcher,
    MultilineStringMatcher,
    QuotedStringMatcher,
    new RegexTokenMatcher(".".r, BadCharacter, always, identity)
  )


  def isHoconWhitespace(char: Char): Boolean = char.isWhitespace || SpecialWhitespace.contains(char)

  def isCStyleComment(seq: CharSequence, index: Int): Boolean =
    seq.subSequence(index, seq.length).startsWith("//")

  def continuesUnquotedChars(seq: CharSequence, index: Int): Boolean = index < seq.length && {
    val char = seq.charAt(index)
    char != '.' && !ForbiddenChars.contains(char) && !isHoconWhitespace(char) && !isCStyleComment(seq, index)
  }

  object QuotedStringMatcher extends TokenMatcher {
    def matchToken(seq: CharSequence, state: State): Option[TokenMatch] = if (seq.charAt(0) == '\"') {
      @tailrec
      def drain(offset: Int, escaping: Boolean): Int =
        if (offset < seq.length) {
          seq.charAt(offset) match {
            case '\n' => offset
            case '\"' if !escaping => offset + 1
            case '\\' if !escaping => drain(offset + 1, escaping = true)
            case _ => drain(offset + 1, escaping = false)
          }
        } else offset

      Some(TokenMatch(QuotedString, drain(1, escaping = false), onContents(state)))
    } else None
  }

  object MultilineStringMatcher extends TokenMatcher {
    def matchToken(seq: CharSequence, state: State): Option[TokenMatch] =
      if (seq.startsWith("\"\"\"")) {
        val strWithoutOpening = seq.subSequence(3, seq.length)
        val length = HoconConstants.MultilineStringEnd.findFirstMatchIn(strWithoutOpening)
          .map(m => m.end + 3).getOrElse(seq.length)

        Some(TokenMatch(MultilineString, length, onContents(state)))
      } else None
  }

  object UnquotedCharsMatcher extends TokenMatcher {
    def matchToken(seq: CharSequence, state: State): Option[TokenMatch] = {
      var c = 0
      while (continuesUnquotedChars(seq, c)) {
        c += 1
      }
      if (c > 0) Some(TokenMatch(UnquotedChars, c, onContents(state))) else None
    }
  }

  object WhitespaceMatcher extends TokenMatcher {
    def matchToken(seq: CharSequence, state: State): Option[TokenMatch] = {
      var c = 0
      var nl = false

      def char = seq.charAt(c)

      while (c < seq.length && isHoconWhitespace(char)) {
        nl ||= char == '\n'
        c += 1
      }
      if (c > 0) {
        val token = if (nl) LineBreakingWhitespace else InlineWhitespace
        Some(TokenMatch(token, c, newState(state, nl)))
      } else None
    }

    def newState(state: State, newLine: Boolean): State = state match {
      case _ if newLine => Initial
      case SubStarting => Value
      case SubStarted => Substitution
      case _ => state
    }
  }

  private var input: CharSequence = _
  private var endOffset: Int = _
  private var stateBefore: State = Initial
  private var stateAfter: State = Initial

  private var tokenStart: Int = _
  private var tokenEnd: Int = _
  private var token: IElementType = _

  def getBufferEnd: Int = endOffset

  def getBufferSequence: CharSequence = input

  def advance(): Unit = {
    tokenStart = tokenEnd
    val seq = input.subSequence(tokenStart, endOffset)
    if (seq.length > 0) {

      val TokenMatch(newToken, length, newState) =
        matchers.iterator.flatMap(_.matchToken(seq, stateAfter)).next()

      tokenEnd = tokenStart + length
      token = newToken
      stateBefore = stateAfter
      stateAfter = newState
    } else {
      stateBefore = Initial
      stateAfter = Initial
      token = null
    }
  }

  def getTokenEnd: Int = tokenEnd

  def getTokenStart: Int = tokenStart

  def getTokenType: IElementType = {
    if (token == null) {
      advance()
    }
    token
  }

  def getState: Int = stateBefore.raw

  def start(buffer: CharSequence, startOffset: Int, endOffset: Int, initialState: Int): Unit = {
    this.token = null
    this.input = buffer
    this.tokenStart = startOffset
    this.tokenEnd = startOffset
    this.endOffset = endOffset
    this.stateBefore = States(initialState)
  }
}
