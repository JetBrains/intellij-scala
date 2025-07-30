package org.jetbrains.plugins.scala.lang.scaladoc.parser.parsing.markdown

import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.parser.LookaheadText
import org.intellij.markdown.parser.ProductionHolder
import org.intellij.markdown.parser.MarkerProcessor
import org.intellij.markdown.parser.constraints.MarkdownConstraints
import org.intellij.markdown.parser.markerblocks.{MarkerBlock, MarkerBlockProvider}
import org.intellij.markdown.parser.sequentialparsers.SequentialParser
import org.jetbrains.plugins.scala.lang.scaladoc.parser.parsing.{MarkdownCompanionProxy, MyScaladocParsing}

import java.util
import kotlin.ranges.IntRange

class ScalaDocTagMarkerBlockProvider extends MarkerBlockProvider[MarkerProcessor.StateInfo] {
  override def createMarkerBlocks(position: LookaheadText#Position, productionHolder: ProductionHolder, t: MarkerProcessor.StateInfo): util.List[MarkerBlock] = {
    // Check if the current position is at the start of a line (after whitespace) and contains a tag (@)
    ScalaDocMarkdownFlavour.getTagOnLine(position) match {
      case Some(ScalaDocMarkdownFlavour.TagInfo(tagStart, tagEnd, argument)) =>
        productionHolder.addProduction(util.List.of(new SequentialParser.Node(new IntRange(position.getOffset + tagStart, position.getOffset + tagEnd), ScalaDocTagMarkerBlock.TAG_NAME)))

        argument.foreach {
          case (argStart, argEnd) =>
            productionHolder.addProduction(util.List.of(
              new SequentialParser.Node(new IntRange(position.getOffset + argStart, position.getOffset + argEnd), ScalaDocTagMarkerBlock.TAG_ARGUMENT)
            ))
        }

        util.List.of(new ScalaDocTagMarkerBlock(t.getCurrentConstraints, productionHolder.mark()))
      case None => util.List.of()
    }
  }

  override def interruptsParagraph(position: LookaheadText#Position, markdownConstraints: MarkdownConstraints): Boolean = {
    // If there is another tag right here, paragraphs are interrupted
    ScalaDocMarkdownFlavour.getTagOnLine(position).isDefined
  }
}