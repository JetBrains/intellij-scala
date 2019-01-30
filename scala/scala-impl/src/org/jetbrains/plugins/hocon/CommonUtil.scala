package org.jetbrains.plugins.hocon

import java.net.{MalformedURLException, URL}
import java.{lang => jl, util => ju}

import com.intellij.lang.ASTNode
import com.intellij.openapi.util.TextRange
import com.intellij.psi.tree.{IElementType, TokenSet}
import com.intellij.psi.{PsiElement, PsiWhiteSpace}
import org.jetbrains.plugins.hocon.lexer.HoconTokenType

import scala.collection.GenTraversableOnce
import scala.language.implicitConversions

object CommonUtil {

  def notWhiteSpaceSibling(element: PsiElement)
                          (sibling: PsiElement => PsiElement): PsiElement = {
    var result = sibling(element)
    while (isWhiteSpace(result)) {
      result = sibling(result)
    }
    result
  }

  private[this] def isWhiteSpace(element: PsiElement): Boolean = element match {
    case null => false
    case _: PsiWhiteSpace => true
    case _ => element.getNode.getElementType match {
      case HoconTokenType.InlineWhitespace => true
      case _ => false
    }
  }

  implicit def liftSingleToken(token: IElementType): TokenSet = TokenSet.create(token)

  implicit class TokenSetOps(val tokenSet: TokenSet) {
    def |(otherTokenSet: TokenSet): TokenSet =
      TokenSet.orSet(tokenSet, otherTokenSet)

    def &(otherTokenSet: TokenSet): TokenSet =
      TokenSet.andSet(tokenSet, otherTokenSet)

    def &^(otherTokenSet: TokenSet): TokenSet =
      TokenSet.andNot(tokenSet, otherTokenSet)

    def unapply(tokenType: IElementType): Boolean =
      tokenSet.contains(tokenType)

    val extractor = this
  }

  implicit def token2TokenSetOps(token: IElementType): TokenSetOps = new TokenSetOps(token)

  implicit class CharSequenceOps(val cs: CharSequence) extends AnyVal {
    def startsWith(str: String): Boolean =
      cs.length >= str.length && str.contentEquals(cs.subSequence(0, str.length))

    def charIterator: Iterator[Char] =
      Iterator.range(0, cs.length).map(cs.charAt)
  }

  implicit class NodeOps(val node: ASTNode) extends AnyVal {
    def childrenIterator: Iterator[ASTNode] =
      Iterator.iterate(node.getFirstChildNode)(_.getTreeNext).takeWhile(_ != null)

    def children: Seq[ASTNode] =
      childrenIterator.toVector: Seq[ASTNode]

    def hasSingleChild: Boolean =
      node.getFirstChildNode != null && node.getFirstChildNode.getTreeNext == null

  }

  implicit class StringOps(val str: String) extends AnyVal {
    def indent(ind: String): String =
      ind + str.replaceAllLiterally("\n", "\n" + ind)
  }

  implicit class any2opt[T](val t: T) extends AnyVal {
    def opt = Option(t)
  }

  implicit class collectionOps[A](val coll: GenTraversableOnce[A]) extends AnyVal {
    def toJList[B >: A]: ju.List[B] = {
      val result = new ju.ArrayList[B]
      coll.foreach(result.add)
      result
    }
  }

  private val quotedCharPattern = "\\\\[\\\\\"/bfnrt]".r
  private val quotedUnicodePattern = "\\\\u([0-9A-Fa-f]{4})".r

  def unquote(str: String): String = {
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

  def uncaps(str: String): String =
    str.replace('_', ' ').toLowerCase

  object TextRange {
    def unapply(textRange: TextRange) =
      Some((textRange.getStartOffset, textRange.getEndOffset))

    def apply(start: Int, end: Int): TextRange =
      com.intellij.openapi.util.TextRange.create(start, end)

    def unionFrom(first: PsiElement, elements: PsiElement*): TextRange =
      elements.map(_.getTextRange).fold(first.getTextRange)(_ union _)
  }

  def isValidUrl(str: String): Boolean =
    try {
      new URL(str)
      true
    } catch {
      case _: MalformedURLException => false
    }
}
