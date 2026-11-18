package org.jetbrains.plugins.scala.lang.scaladoc.parser.parsing.markdown

import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.parser.constraints.MarkdownConstraints
import org.intellij.markdown.parser.markerblocks.impl.CodeFenceMarkerBlock
import org.intellij.markdown.parser.markerblocks.providers.CodeFenceProvider
import org.intellij.markdown.parser.markerblocks.providers.CodeFenceProvider.OpeningInfo
import org.intellij.markdown.parser.markerblocks.{MarkerBlock, MarkerBlockProvider}
import org.intellij.markdown.parser.sequentialparsers.SequentialParser
import org.intellij.markdown.parser.{LookaheadText, MarkerProcessor, ProductionHolder}

import java.util
import kotlin.ranges.IntRange

/**
 * Provides Markdown backtick/tilde fences and legacy ScalaDoc brace fences.
 *
 * Extends [[CodeFenceProvider]] to preserve standard Markdown opening and language parsing
 * while allowing arbitrary indentation before backtick closing fences.
 */
class ScalaDocCodeFenceProvider extends CodeFenceProvider {
  override def createMarkerBlocks(position: LookaheadText#Position, productionHolder: ProductionHolder, t: MarkerProcessor.StateInfo): util.List[MarkerBlock] = {
    // Code blocks should only start at the start of a line in Markdown mode, according to ScalaDoc-the-tool.
    val braceFenceEnd = fenceEnd(position, t.getCurrentConstraints, "{{{")
    braceFenceEnd match {
      case Some(end) =>
        createBraceCodeFenceBlocks(position, productionHolder, t.getCurrentConstraints, end)
      case None =>
        super.createMarkerBlocks(position, productionHolder, t)
    }
  }

  private def createBraceCodeFenceBlocks(position: LookaheadText#Position, productionHolder: ProductionHolder, constraints: MarkdownConstraints, end: Int): util.List[MarkerBlock] = {
    val fenceStartNode = new SequentialParser.Node(
      new IntRange(position.getOffset, position.getOffset + end),
      MarkdownTokenTypes.CODE_FENCE_START
    )
    productionHolder.addProduction(util.List.of(fenceStartNode))

    // HACK: This is kind of a trick: the last argument is officially the "fence" but it's really only used in a regex
    //       That's why we have to escape it, and that's why it's the *closing* fence
    //       Unlike Markdown fences, a ScalaDoc closing fence can have arbitrary indentation (e.g. under @note).
    val codeFenceMarkerBlock = new CodeFenceMarkerBlock(constraints, productionHolder, "[ \\t]*\\}\\}\\}")
    util.List.of(codeFenceMarkerBlock)
  }

  override def interruptsParagraph(position: LookaheadText#Position, markdownConstraints: MarkdownConstraints): Boolean =
    fenceEnd(position, markdownConstraints, "{{{").isDefined || super.interruptsParagraph(position, markdownConstraints)

  override protected def obtainFenceOpeningInfo(position: LookaheadText#Position, constraints: MarkdownConstraints): OpeningInfo = {
    val opening = super.obtainFenceOpeningInfo(position, constraints)
    val isBacktickFence = opening != null && opening.getDelimiter.startsWith("`")
    if (isBacktickFence) {
      // The delimiter is embedded in CodeFenceMarkerBlock's closing-fence regex.
      // ScalaDoc allows arbitrary indentation here, e.g. aligned with an @example body.
      new OpeningInfo("[ \\t]*" + opening.getDelimiter, opening.getInfo)
    } else
      opening
  }

  private def fenceEnd(position: LookaheadText#Position, constraints: MarkdownConstraints, fence: String): Option[Int] = {
    if (
      !MarkerBlockProvider.Companion.isStartOfLineWithConstraints(position, constraints)
    )
      None
    else {
      val sequence = position.getCurrentLineFromPosition
      val start = MarkerBlockProvider.Companion.passSmallIndent(sequence, 0)
      val trimmed = sequence.subSequence(start, sequence.length())

      if (trimmed.length() >= fence.length &&
        trimmed.subSequence(0, fence.length).toString == fence)
        Some(start + 3)
      else None
    }
  }
}
