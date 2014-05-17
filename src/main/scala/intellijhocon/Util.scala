package intellijhocon

import com.intellij.lang.ASTNode
import com.intellij.psi.tree.{IElementType, TokenSet}

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

}
