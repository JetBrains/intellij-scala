package intellijhocon

import com.intellij.psi.tree.TokenSet

object HoconTokenSets {

  import HoconTokenType._

  implicit def liftSingleToken(token: HoconTokenType): TokenSet =
    TokenSet.create(token)

  implicit class TokenSetOps(private val tokenSet: TokenSet) extends AnyVal {
    def |(otherTokenSet: TokenSet) =
      TokenSet.orSet(tokenSet, otherTokenSet)

    def &(otherTokenSet: TokenSet) =
      TokenSet.andSet(tokenSet, otherTokenSet)

    def &^(otherTokenSet: TokenSet) =
      TokenSet.andNot(tokenSet, otherTokenSet)
  }

  implicit def token2TokenSetOps(token: HoconTokenType) =
    new TokenSetOps(token)

  val Empty = TokenSet.EMPTY
  val Whitespace = InlineWhitespace | LineBreakingWhitespace
  val Comment = HashComment | DoubleSlashComment
  val WhitespaceOrComment = Whitespace | Comment
  val StringLiteral = QuotedString | MultilineString
  val PathValueSeparator = Colon | Equals | PlusEquals
  val ArrayElementsEnding = RBracket | RBrace
  val ValueEnding = Comma | RBrace | RBracket
  val PathEnding = PathValueSeparator | LBrace | RefRBrace | ValueEnding
  val KeyEnding = PathEnding | Period
  val ValueUnquotedChars = UnquotedChars | Period
  val SimpleValuePart = UnquotedChars | Period | StringLiteral
  val PathStart = UnquotedChars | StringLiteral | Period | BadCharacter
  val ReferencePathStart = PathStart | PathValueSeparator
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
