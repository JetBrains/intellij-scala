package org.jetbrains.plugins.scala.lang.scaladoc.parser.parsing.markdown

import org.intellij.markdown.parser.LookaheadText
import org.intellij.markdown.parser.ProductionHolder
import org.intellij.markdown.parser.MarkerProcessor
import org.intellij.markdown.parser.constraints.MarkdownConstraints
import org.intellij.markdown.parser.markerblocks.{MarkerBlock, MarkerBlockProvider}
import org.intellij.markdown.parser.markerblocks.impl.CodeFenceMarkerBlock
import org.intellij.markdown.parser.sequentialparsers.SequentialParser
import org.intellij.markdown.MarkdownTokenTypes
import org.jetbrains.plugins.scala.lang.scaladoc.parser.parsing.MarkdownCompanionProxy

import java.util
import kotlin.ranges.IntRange

class ScalaDocCodeFenceProvider extends MarkerBlockProvider[MarkerProcessor.StateInfo] {
  override def createMarkerBlocks(position: LookaheadText#Position, productionHolder: ProductionHolder, t: MarkerProcessor.StateInfo): util.List[MarkerBlock] = {
    // Code blocks should only start at the start of a line in Markdown mode, according to ScalaDoc-the-tool.
    fenceEnd(position, t.getCurrentConstraints, "{{{") match {
      case Some(end) =>
        productionHolder.addProduction(util.List.of(new SequentialParser.Node(
          new IntRange(position.getOffset, position.getOffset + end),
          MarkdownTokenTypes.CODE_FENCE_START
        )))

        // HACK: This is kind of a trick: the last argument is officially the "fence" but it's really only used in a regex
        //       That's why we have to escape it, and that's why it's the *closing* fence
        util.List.of(new CodeFenceMarkerBlock(t.getCurrentConstraints, productionHolder, "\\}\\}\\}"))
      case None => util.List.of()
    }
  }

  override def interruptsParagraph(position: LookaheadText#Position, markdownConstraints: MarkdownConstraints): Boolean =
    fenceEnd(position, markdownConstraints, "{{{").isDefined

  private def fenceEnd(position: LookaheadText#Position, constraints: MarkdownConstraints, fence: String): Option[Int] = {
    if (
      !MarkdownCompanionProxy.MarkerBlockProviderCompanion.isStartOfLineWithConstraints(position, constraints)
    )
      None
    else {
      val sequence = position.getCurrentLineFromPosition
      val start = MarkdownCompanionProxy.MarkerBlockProviderCompanion.passSmallIndent(sequence, 0)
      val trimmed = sequence.subSequence(start, sequence.length())

      if (trimmed.length() >= fence.length &&
        trimmed.subSequence(0, fence.length).toString == fence)
        Some(start + 3)
      else None
    }
  }
}