package intellijhocon
package lexer

import com.intellij.psi.tree.TokenSet
import intellijhocon.Util

object HoconTokenSets {

  import HoconTokenType._
  import Util._

  val Empty = TokenSet.EMPTY
  val Whitespace = InlineWhitespace | LineBreakingWhitespace
  val Comment = HashComment | DoubleSlashComment
  val WhitespaceOrComment = Whitespace | Comment
  val StringLiteral = QuotedString | MultilineString
  val PathValueSeparator = Colon | Equals | PlusEquals
  val ArrayElementsEnding = RBracket | RBrace
  val ValueEnding = Comma | RBrace | RBracket
  val PathEnding = PathValueSeparator | LBrace | SubRBrace | ValueEnding
  val KeyEnding = PathEnding | Period
  val ValueUnquotedChars = UnquotedChars | Period
  val SimpleValuePart = UnquotedChars | Period | StringLiteral
  val PathStart = UnquotedChars | StringLiteral | Period | BadCharacter
  val SubstitutionPathStart = PathStart | PathValueSeparator
  val ValueStart = SimpleValuePart | LBrace | LBracket | Dollar | PathValueSeparator | BadCharacter
  val ObjectEntryStart = PathStart | UnquotedChars

  case class Matcher(tokenSet: TokenSet, requireNoNewLine: Boolean, matchNewLine: Boolean, matchEof: Boolean) {
    def noNewLine = copy(requireNoNewLine = true)

    def orNewLineOrEof = copy(matchNewLine = true, matchEof = true)

    def orEof = copy(matchEof = true)
  }

  implicit def token2Matcher(token: HoconTokenType): Matcher =
    Matcher(TokenSet.create(token), requireNoNewLine = false, matchNewLine = false, matchEof = false)

  implicit def tokenSet2Matcher(tokenSet: TokenSet): Matcher =
    Matcher(tokenSet, requireNoNewLine = false, matchNewLine = false, matchEof = false)
}
