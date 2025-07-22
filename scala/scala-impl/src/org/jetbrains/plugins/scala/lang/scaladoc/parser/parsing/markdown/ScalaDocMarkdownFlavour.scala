package org.jetbrains.plugins.scala.lang.scaladoc.parser.parsing.markdown

import org.intellij.markdown.IElementType
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.flavours.MarkdownFlavourDescriptor
import org.intellij.markdown.flavours.commonmark.CommonMarkFlavourDescriptor
import org.intellij.markdown.html._
import org.intellij.markdown.lexer.MarkdownLexer
import org.intellij.markdown.lexer._MarkdownLexer
import org.intellij.markdown.parser.{LinkMap, LookaheadText, MarkerProcessor, MarkerProcessorFactory, ProductionHolder}
import org.intellij.markdown.parser.sequentialparsers.EmphasisLikeParser
import org.intellij.markdown.parser.sequentialparsers.SequentialParserManager
import org.intellij.markdown.parser.sequentialparsers.impl._
import org.jetbrains.plugins.scala.lang.scaladoc.parser.parsing.MarkdownCompanionProxy

import java.net.URI
import java.util
import scala.jdk.CollectionConverters._

class ScalaDocMarkdownFlavour extends CommonMarkFlavourDescriptor {
  override def getMarkerProcessorFactory: MarkerProcessorFactory = new MarkerProcessorFactory {
    override def createMarkerProcessor(productionHolder: ProductionHolder): MarkerProcessor[_] = {
      new ScalaDocMarkerProcessor(productionHolder, ScalaDocMarkdownConstraints.BASE)
    }
  }

  override def createHtmlGeneratingProviders(linkMap: LinkMap, uri: URI): java.util.Map[IElementType, GeneratingProvider] = {
    // TODO: Some are unimplemented.
    val parent = super.createHtmlGeneratingProviders(linkMap, uri)
    parent.putAll(
      Map(
        // ScalaDoc tags
        ScalaDocTagMarkerBlock.TAG_BLOCK -> new SimpleTagProvider("div"),
      ).asJava
    )

    parent
  }
}

object ScalaDocMarkdownFlavour {
  /**
   * Checks for the existence of an @ tag on a line.
   *
   * @param position Where to search from. Must be at the start of a line.
   * @return None if no tag exists, a range [start, end) otherwise, which includes the @ and is relative to the current position.
   */
  def getTagOnLine(position: LookaheadText#Position): Option[(Int, Int)] = {
    if (position == null || position.getOffsetInCurrentLine > 0) {
      return None
    }

    // Skip whitespace at the beginning of the line
    val currentLine = position.getCurrentLine
    val start = position.charsToNonWhitespace() + position.getOffsetInCurrentLine

    // Check if the first non-whitespace character is '@'
    if (0 <= start && start < currentLine.length() && currentLine.charAt(start) == '@') {
      val lineLength = currentLine.length()
      val tagEnd = (start + 1 until lineLength).find { i =>
        val c = currentLine.charAt(i)
        Character.isWhitespace(c)
      }.getOrElse(lineLength)

      if (tagEnd == start + 1) None
      else Some((start, tagEnd))
    } else None
  }
}