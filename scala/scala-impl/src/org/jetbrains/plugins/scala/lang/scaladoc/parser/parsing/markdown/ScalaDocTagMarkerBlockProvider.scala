package org.jetbrains.plugins.scala.lang.scaladoc.parser.parsing.markdown

import org.intellij.markdown.parser.LookaheadText
import org.intellij.markdown.parser.ProductionHolder
import org.intellij.markdown.parser.MarkerProcessor
import org.intellij.markdown.parser.constraints.MarkdownConstraints
import org.intellij.markdown.parser.markerblocks.{MarkerBlock, MarkerBlockProvider}
import org.intellij.markdown.parser.sequentialparsers.SequentialParser
import org.jetbrains.plugins.scala.lang.scaladoc.parser.parsing.MarkdownCompanionProxy

import java.util
import kotlin.ranges.IntRange

class ScalaDocTagMarkerBlockProvider extends MarkerBlockProvider[MarkerProcessor.StateInfo] {
  override def createMarkerBlocks(position: LookaheadText#Position, productionHolder: ProductionHolder, t: MarkerProcessor.StateInfo): util.List[MarkerBlock] = {
    // Check if the current position is at the start of a line (after whitespace) and contains a tag (@)
    getTagInfo(position, t.getCurrentConstraints) match {
      case Some((start, end)) =>
        productionHolder.addProduction(util.List.of(new SequentialParser.Node(new IntRange(position.getOffset + start, position.getOffset + end), ScalaDocTagMarkerBlock.TAG_NAME)))

        util.List.of(new ScalaDocTagMarkerBlock(t.getCurrentConstraints, productionHolder.mark()))
      case None => util.List.of()
    }
  }

  override def interruptsParagraph(position: LookaheadText#Position, markdownConstraints: MarkdownConstraints): Boolean = {
    // If there is another tag right here, we get interrupted (? I think that's what this method does. Unclear)
    getTagInfo(position, markdownConstraints).isDefined
  }

  private def getTagInfo(position: LookaheadText#Position, constraints: MarkdownConstraints): Option[(Int, Int)] = {
    // Check if we're at the start of a line
    if (!MarkdownCompanionProxy.MarkerBlockProviderCompanion.isStartOfLineWithConstraints(position, constraints)) {
      return None
    }

    // Skip whitespace at the beginning of the line
    val currentLine = position.getCurrentLineFromPosition
    val start = position.charsToNonWhitespace()

    // Check if the first non-whitespace character is '@'
    if (start < currentLine.length() && currentLine.charAt(start) == '@') {
      val lineLength = currentLine.length()
      val tagEnd = (start + 1 until lineLength).find { i =>
        val c = currentLine.charAt(i)
        Character.isWhitespace(c)
      }.getOrElse(lineLength)
      Some((start + 1, tagEnd))
    } else None
  }
}