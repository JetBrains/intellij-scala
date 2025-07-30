package org.jetbrains.plugins.scala.lang.scaladoc.parser.parsing.markdown

import org.intellij.markdown.{MarkdownElementType, MarkdownElementTypes, MarkdownTokenTypes}
import org.intellij.markdown.parser.sequentialparsers.RangesListBuilder
import org.intellij.markdown.parser.sequentialparsers.SequentialParser
import org.intellij.markdown.parser.sequentialparsers.TokensCache
import org.jetbrains.plugins.scala.lang.scaladoc.parser.parsing.markdown.WikiLinkParser.WIKI_LINK

import kotlin.ranges.IntRange

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
      if (isOpeningBracket(iterator)) {
        val startIndex = iterator.getIndex
        // Advance twice; each '[' is one token.
        iterator = iterator.advance().advance()

        while (iterator.getType != null && !isClosingBracket(iterator)) {
          iterator = iterator.advance()
        }

        if (iterator.getType != null && isClosingBracket(iterator)) {

          val range = new IntRange(startIndex, iterator.getIndex + 2)
          result.withNode(new SequentialParser.Node(range, WIKI_LINK))

          // Advance twice; each ']' is one token.
          iterator = iterator.advance().advance()
        } else {
          iterator = new tokens.RangesListIterator(rangesToGlue)
          iterator = advanceToIndex(iterator, startIndex + 1)
        }
      } else {
        delegateIndices.put(iterator.getIndex)
        iterator = iterator.advance()
      }
    }

    result.withFurtherProcessing(delegateIndices.get())
  }

  private def isOpeningBracket(iterator: TokensCache#RangesListIterator): Boolean =
    iterator.getType == MarkdownTokenTypes.LBRACKET &&
      iterator.advance().getType == MarkdownTokenTypes.LBRACKET

  private def isClosingBracket(iterator: TokensCache#RangesListIterator): Boolean =
    iterator.getType == MarkdownTokenTypes.RBRACKET &&
      iterator.advance().getType == MarkdownTokenTypes.RBRACKET

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
}