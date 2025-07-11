package org.jetbrains.plugins.scala.lang.scaladoc.parser.parsing.markdown

import org.intellij.markdown.IElementType
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.flavours.MarkdownFlavourDescriptor
import org.intellij.markdown.html._
import org.intellij.markdown.lexer.MarkdownLexer
import org.intellij.markdown.lexer._MarkdownLexer
import org.intellij.markdown.parser.LinkMap
import org.intellij.markdown.parser.MarkerProcessorFactory
import org.intellij.markdown.parser.MarkerProcessor
import org.intellij.markdown.parser.ProductionHolder
import org.intellij.markdown.parser.sequentialparsers.EmphasisLikeParser
import org.intellij.markdown.parser.sequentialparsers.SequentialParserManager
import org.intellij.markdown.parser.sequentialparsers.impl._
import org.jetbrains.plugins.scala.lang.scaladoc.parser.parsing.MarkdownCompanionProxy

import java.net.URI
import java.util
import scala.jdk.CollectionConverters._

class ScalaDocMarkdownFlavour extends MarkdownFlavourDescriptor {
  override def getMarkerProcessorFactory: MarkerProcessorFactory = new MarkerProcessorFactory {
    override def createMarkerProcessor(productionHolder: ProductionHolder): MarkerProcessor[_] = {
      new ScalaDocMarkerProcessor(productionHolder, MarkdownCompanionProxy.CommonMarkdownConstraintsCompanion.getBASE)
    }
  }

  override def createInlinesLexer(): MarkdownLexer = {
    new MarkdownLexer(new _MarkdownLexer())
  }

  override def getSequentialParserManager: SequentialParserManager = {
    new SequentialParserManager() {
      override def getParserSequence = {
        List(
          new AutolinkParser(List(MarkdownTokenTypes.AUTOLINK).asJava),
          new BacktickParser(),
          new ImageParser(),
          new InlineLinkParser(),
          new ReferenceLinkParser(),
          new EmphasisLikeParser(new EmphStrongDelimiterParser())
        ).asJava
      }
    }
  }

  override def createHtmlGeneratingProviders(linkMap: LinkMap, uri: URI): java.util.Map[IElementType, GeneratingProvider] = {
    // TODO: Some are unimplemented.
    Map(
      // Basic elements
      MarkdownElementTypes.MARKDOWN_FILE -> new SimpleTagProvider("body"),
      MarkdownElementTypes.HTML_BLOCK -> new HtmlBlockGeneratingProvider(),
      // MarkdownTokenTypes.HTML_TAG -> ???,
      MarkdownElementTypes.BLOCK_QUOTE -> new SimpleTagProvider("blockquote"),

      // Lists
      // MarkdownElementTypes.ORDERED_LIST -> ???,
      MarkdownElementTypes.UNORDERED_LIST -> new SimpleTagProvider("ul"),
      MarkdownElementTypes.LIST_ITEM -> new ListItemGeneratingProvider(),

      // Headers
      MarkdownTokenTypes.SETEXT_CONTENT -> new TrimmingInlineHolderProvider(),
      MarkdownElementTypes.SETEXT_1 -> new SimpleTagProvider("h1"),
      MarkdownElementTypes.SETEXT_2 -> new SimpleTagProvider("h2"),
      MarkdownTokenTypes.ATX_CONTENT -> new TrimmingInlineHolderProvider(),
      MarkdownElementTypes.ATX_1 -> new SimpleTagProvider("h1"),
      MarkdownElementTypes.ATX_2 -> new SimpleTagProvider("h2"),
      MarkdownElementTypes.ATX_3 -> new SimpleTagProvider("h3"),
      MarkdownElementTypes.ATX_4 -> new SimpleTagProvider("h4"),
      MarkdownElementTypes.ATX_5 -> new SimpleTagProvider("h5"),
      MarkdownElementTypes.ATX_6 -> new SimpleTagProvider("h6"),

      // Links
      // MarkdownElementTypes.AUTOLINK -> ???,
      MarkdownElementTypes.LINK_LABEL -> new TransparentInlineHolderProvider(),
      MarkdownElementTypes.LINK_TEXT -> new TransparentInlineHolderProvider(),
      MarkdownElementTypes.LINK_TITLE -> new TransparentInlineHolderProvider(),
      // MarkdownElementTypes.IMAGE -> ???,
      // MarkdownElementTypes.LINK_DEFINITION -> ???,

      // Code
      MarkdownElementTypes.CODE_FENCE -> new CodeFenceGeneratingProvider(),
      // MarkdownElementTypes.CODE_BLOCK -> ???,

      // MarkdownTokenTypes.HORIZONTAL_RULE -> ???,
      // MarkdownTokenTypes.HARD_LINE_BREAK -> ???,
      // MarkdownElementTypes.PARAGRAPH -> ???,

      // Formatting
      MarkdownElementTypes.EMPH -> new SimpleInlineTagProvider("em", 1, -1),
      MarkdownElementTypes.STRONG -> new SimpleInlineTagProvider("strong", 2, -2),
      MarkdownElementTypes.CODE_SPAN -> new CodeSpanGeneratingProvider(),

      // ScalaDoc tags
      ScalaDocTagMarkerBlock.TAG_BLOCK -> new SimpleTagProvider("div")
    ).asJava
  }
}