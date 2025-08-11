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
import org.intellij.markdown.parser.sequentialparsers.{EmphasisLikeParser, SequentialParser, SequentialParserManager}
import org.intellij.markdown.parser.sequentialparsers.impl._
import org.jetbrains.plugins.scala.lang.scaladoc.parser.parsing.{MarkdownCompanionProxy, MyScaladocParsing}

import java.net.URI
import java.util
import scala.jdk.CollectionConverters._

class ScalaDocMarkdownFlavour extends CommonMarkFlavourDescriptor {
  override def getMarkerProcessorFactory: MarkerProcessorFactory = new MarkerProcessorFactory {
    override def createMarkerProcessor(productionHolder: ProductionHolder): MarkerProcessor[_] = {
      new ScalaDocMarkerProcessor(productionHolder, ScalaDocMarkdownConstraints.BASE)
    }
  }

  private val sequentialParserManager = new SequentialParserManager {
    override def getParserSequence: util.List[SequentialParser] = util.List.of(
      new AutolinkParser(util.List.of(MarkdownTokenTypes.AUTOLINK)),
      new BacktickParser(),
      new ImageParser(),
      new WikiLinkParser(),
      new InlineLinkParser(),
      new ReferenceLinkParser(),
      new EmphasisLikeParser(new EmphStrongDelimiterParser())
    )
  }
  override def getSequentialParserManager: SequentialParserManager = sequentialParserManager

  override def createHtmlGeneratingProviders(linkMap: LinkMap, uri: URI): java.util.Map[IElementType, GeneratingProvider] = {
    // TODO: Some are unimplemented.
    val parent = super.createHtmlGeneratingProviders(linkMap, uri)
    parent.putAll(
      Map(
        // ScalaDoc tags
        ScalaDocTagMarkerBlock.TAG_BLOCK -> new SimpleTagProvider("div"),
        ScalaDocTagMarkerBlock.TAG_NAME -> new SimpleTagProvider("span"),
        ScalaDocTagMarkerBlock.TAG_ARGUMENT -> new SimpleTagProvider("span"),
      ).asJava
    )

    parent
  }

  override def createInlinesLexer(): MarkdownLexer = super.createInlinesLexer()
}

object ScalaDocMarkdownFlavour {
  /**
   * Information about a tag on a line. All positions are relative to the start of the line.
   *
   * @param start the start of the tag, including the @
   * @param end the end of the tag itself
   * @param argument if it exists, the start and end of the argument
   */
  case class TagInfo(val start: Int, val end: Int, val argument: Option[(Int, Int)]) {
    def bodyStart: Int = argument.map(_._2).getOrElse(end)
  }

  /**
   * Checks for the existence of an @ tag on a line.
   *
   * @param position Where to search from. Must be at the start of a line.
   * @return None if no tag exists, a [[TagInfo]] otherwise
   */
  def getTagOnLine(position: LookaheadText#Position): Option[TagInfo] = {
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

      if (tagEnd == start + 1)
        return None

      // Argument processing
      val tag = currentLine.substring(start + 1, tagEnd)
      val argument = Option.when(MyScaladocParsing.TagNames.TagNamesWithParameters.contains(tag)) {
        // Find the argument (simplified version without delimiters)
        // TODO: Tag arguments can be delimited by ` and include spaces then
        nextMatchingChar(currentLine, tagEnd, c => !Character.isWhitespace(c)).map(argStart => {
          val argEnd = nextMatchingChar(currentLine, argStart, Character.isWhitespace).getOrElse(currentLine.length)
          (argStart, argEnd)
        })
      }.flatten

      Some(new TagInfo(start, tagEnd, argument))
    } else None
  }

  private def nextMatchingChar(line: CharSequence, from: Int, predicate: Char => Boolean): Option[Int] = {
    (from until line.length)
      .find(i => predicate(line.charAt(i)))
  }
}