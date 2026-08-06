package org.jetbrains.plugins.scala.lang.scaladoc.parser.parsing.markdown

import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.parser.sequentialparsers.{RangesListBuilder, SequentialParser, TokensCache}
import org.intellij.markdown.{MarkdownElementType, MarkdownTokenTypes}
import org.jetbrains.plugins.scala.lang.scaladoc.parser.parsing.markdown.WikiLinkParser.ChildrenInfo.{astNodeLen, calcClosingTokens}
import org.jetbrains.plugins.scala.lang.scaladoc.parser.parsing.markdown.WikiLinkParser.WIKI_LINK

import kotlin.ranges.IntRange
import scala.annotation.tailrec
import scala.collection.immutable.ArraySeq

// TODO:
// Process the interior.
// If after the '[[' there's `\s*http(s)?:` (regex for simplicity), it's assumed to be an HTTP link
// Otherwise it's a code reference link. See _ScalaDocLexer.flex
class WikiLinkParser extends SequentialParser {
  override def parse(tokens: TokensCache, rangesToGlue: java.util.List[IntRange]): SequentialParser.ParsingResult = {
    val result = new SequentialParser.ParsingResultBuilder()
    val delegateIndices = new RangesListBuilder()
    var iterator: TokensCache#RangesListIterator = new tokens.RangesListIterator(rangesToGlue)

    while (iterator.getType != null) {
      isOpeningBracket(iterator) match {
        case Some(bracketLength) =>
          val startIndex = iterator.getIndex
          // Advance twice; each '[' is one token.
          iterator = iterator.advance()

          @tailrec
          def eatUntilClosingBracket(iterator: TokensCache#RangesListIterator): Option[(Int, TokensCache#RangesListIterator)] = {
            val x = isClosingBracket(iterator, bracketLength)
            if (x.nonEmpty || iterator.getType == null) x
            else eatUntilClosingBracket(iterator.advance())
          }

          eatUntilClosingBracket(iterator) match {
            case Some((rangeEnd, closingBracketIterator)) =>
              // `closingBracketIterator` continues parsing, but an earlier sequential parser can make it
              // skip an adjacent inline construct. Use the closing bracket's own end for the Wiki link range
              // so it cannot absorb that already-parsed gap.
              val range = new IntRange(startIndex, rangeEnd)
              result.withNode(new SequentialParser.Node(range, WIKI_LINK))
              iterator = closingBracketIterator
            case None =>
              iterator = new tokens.RangesListIterator(rangesToGlue)
              iterator = advanceToIndex(iterator, startIndex + 1)
          }
        case None =>
          delegateIndices.put(iterator.getIndex)
          iterator = iterator.advance()
      }
    }

    result.withFurtherProcessing(delegateIndices.get())
  }

  private def isOpeningBracket(iterator: TokensCache#RangesListIterator): Option[Int] =
    Option.when(iterator.getType == MarkdownTokenTypes.LBRACKET && iterator.getLength > 1)(iterator.getLength)

  @tailrec
  private def isClosingBracket(iterator: TokensCache#RangesListIterator, expectedLen: Int): Option[(Int, TokensCache#RangesListIterator)] = {
    if (expectedLen < 0) {
      None
    } else if (iterator.getType == MarkdownTokenTypes.RBRACKET) {
      val nextIterator = iterator.advance()
      val remainingBrackets = expectedLen - iterator.getLength
      if (remainingBrackets == 0 && nextIterator.getType != MarkdownTokenTypes.RBRACKET)
        // `nextIterator.getIndex` may point after a code span, autolink, or image removed from rangesToGlue.
        // Keep traversal at that iterator, while delimiting the node immediately after the actual closing bracket.
        Some((iterator.getIndex + 1, nextIterator))
      else
        isClosingBracket(nextIterator, remainingBrackets)
    } else {
      None
    }
  }

  private def advanceToIndex(iterator: TokensCache#RangesListIterator, targetIndex: Int): TokensCache#RangesListIterator = {
    var current = iterator
    while (current.getType != null && current.getIndex < targetIndex) {
      current = current.advance()
    }
    current
  }
}

object WikiLinkParser {
  val WIKI_LINK = new MarkdownElementType("WIKI_LINK", false)

  class ChildrenInfo private(val children: ArraySeq[ASTNode]) {
    val childCount: Int = children.length

    // amount of [ that opens this wiki link
    val bracketCount: Int = astNodeLen(children(0))

    // number of tokens that close this wiki link
    // one of these tokens may contain multiple ]
    lazy val closingBracketTokenCount: Int = calcClosingTokens(children, bracketCount)

    // range is inclusive
    lazy val refTokens: Option[(Int, Int)] = {
      val refTokenCount = children.iterator
        .drop(1) // the initial brackets
        .takeWhile(_.getType == MarkdownTokenTypes.TEXT)
        .length
      Option.when(refTokenCount > 0)(1 -> refTokenCount)
    }

    // range is inclusive
    lazy val descriptionTokens: Option[(Int, Int)] = {
      val start = refTokens.fold(0)(_._2) + 2 // skip the whitespace that comes afterwards
      val end = closingBracketsChildIndex
        .map(_ - 1)
        .getOrElse(children.indices.last)
      Option.when(start <= end)(start -> end)
    }

    def closingBracketsChildIndex: Option[Int] = Option.when(closingBracketTokenCount > 0)(childCount - closingBracketTokenCount)
  }

  object ChildrenInfo {
    def apply(node: ASTNode): ChildrenInfo = {
      assert(node.getType == WIKI_LINK)
      val children = node.getChildren.toArray(Array.empty[ASTNode])
      new ChildrenInfo(ArraySeq.unsafeWrapArray(children))
    }

    private def calcClosingTokens(children: ArraySeq[ASTNode], openingBracketCount: Int): Int = {
      @tailrec
      def check(i: Int, restNeeded: Int): Int = {
        if (i < 1) children.size
        else {
          val child = children(i)
          if (child.getType == MarkdownTokenTypes.RBRACKET) {
            val len = astNodeLen(child)
            if (len >= restNeeded) i
            else check(i - 1, restNeeded - len)
          } else {
            children.size
          }
        }
      }
      children.size - check(children.size - 1, openingBracketCount)
    }

    private def astNodeLen(ast: ASTNode): Int = ast.getEndOffset - ast.getStartOffset
  }
}
