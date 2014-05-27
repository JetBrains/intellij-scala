package intellijhocon

import com.intellij.lang.ASTNode
import com.intellij.psi.tree.{IElementType, TokenSet}
import java.{lang => jl, util => ju}
import com.intellij.openapi.util.TextRange

object Util {

  implicit def liftSingleToken(token: IElementType): TokenSet =
    TokenSet.create(token)

  implicit class TokenSetOps(tokenSet: TokenSet) {
    def |(otherTokenSet: TokenSet) =
      TokenSet.orSet(tokenSet, otherTokenSet)

    def &(otherTokenSet: TokenSet) =
      TokenSet.andSet(tokenSet, otherTokenSet)

    def &^(otherTokenSet: TokenSet) =
      TokenSet.andNot(tokenSet, otherTokenSet)

    def unapply(tokenType: IElementType) =
      tokenSet.contains(tokenType)

    val extractor = this
  }

  implicit def token2TokenSetOps(token: IElementType) =
    new TokenSetOps(token)

  implicit class CharSequenceOps(cs: CharSequence) {
    def startsWith(str: String) =
      cs.length >= str.length && str.contentEquals(cs.subSequence(0, str.length))

    def charIterator =
      Iterator.range(0, cs.length).map(cs.charAt)
  }

  implicit class NodeOps(node: ASTNode) {
    def childrenIterator =
      Iterator.iterate(node.getFirstChildNode)(_.getTreeNext).takeWhile(_ != null)

    def children =
      childrenIterator.toVector: Seq[ASTNode]

    def hasSingleChild =
      node.getFirstChildNode != null && node.getFirstChildNode.getTreeNext == null

  }

  implicit class StringOps(str: String) {
    def indent(ind: String) =
      ind + str.replaceAllLiterally("\n", "\n" + ind)
  }

  implicit class any2opt[T](private val t: T) extends AnyVal {
    def opt = Option(t)
  }

  private val quotedCharPattern = "\\\\[\\\\\"/bfnrt]".r
  private val quotedUnicodePattern = "\\\\u([0-9A-Fa-f]{4})".r

  def unquote(str: String) = {
    var result = str.stripPrefix("\"").stripSuffix("\"")
    result = quotedCharPattern.replaceAllIn(result, m => m.group(0).charAt(1) match {
      case '\\' => "\\"
      case '/' => "/"
      case '"' => "\""
      case 'b' => "\b"
      case 'f' => "\f"
      case 'n' => "\n"
      case 'r' => "\r"
      case 't' => "\t"
    })
    quotedUnicodePattern.replaceAllIn(result, m => jl.Short.parseShort(m.group(1), 16).toChar.toString)
  }

  def uncaps(str: String) =
    str.replace('_', ' ').toLowerCase

  object TextRange {
    def unapply(textRange: TextRange) =
      Some((textRange.getStartOffset, textRange.getEndOffset))

    def apply(start: Int, end: Int) =
      com.intellij.openapi.util.TextRange.create(start, end)
  }

}
