package org.jetbrains.plugins.scala.lang.scaladoc.parser.parsing.markdown

import com.intellij.codeInsight.documentation.DocumentationManagerUtil
import com.intellij.lang.Language
import com.intellij.markdown.utils.CodeFenceSyntaxHighlighterGeneratingProvider
import com.intellij.markdown.utils.lang.HtmlSyntaxHighlighter
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.HtmlChunk
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.flavours.commonmark.CommonMarkFlavourDescriptor
import org.intellij.markdown.html._
import org.intellij.markdown.lexer.MarkdownLexer
import org.intellij.markdown.parser.sequentialparsers.impl._
import org.intellij.markdown.parser.sequentialparsers.{EmphasisLikeParser, SequentialParser, SequentialParserManager}
import org.intellij.markdown.parser.{LinkMap, LookaheadText, MarkerProcessor, MarkerProcessorFactory, ProductionHolder}
import org.intellij.markdown.{IElementType, MarkdownElementTypes, MarkdownTokenTypes}
import org.jetbrains.plugins.scala.Scala3Language
import org.jetbrains.plugins.scala.lang.scaladoc.parser.parsing.MyScaladocParsing

import java.net.URI
import java.util
import scala.jdk.CollectionConverters._

class ScalaDocMarkdownFlavour extends CommonMarkFlavourDescriptor {
  override def getMarkerProcessorFactory: MarkerProcessorFactory =
    (productionHolder: ProductionHolder) => {
      new ScalaDocMarkerProcessor(productionHolder, ScalaDocMarkdownConstraints.BASE)
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
    val parent = super.createHtmlGeneratingProviders(linkMap, uri)
    parent.putAll(
      Map(
        // ScalaDoc tags
        ScalaDocTagMarkerBlock.TAG_BLOCK -> new SimpleTagProvider("div"),
        ScalaDocTagMarkerBlock.TAG_NAME -> new SimpleTagProvider("span"),
        ScalaDocTagMarkerBlock.TAG_ARGUMENT -> new SimpleTagProvider("span"),

        WikiLinkParser.WIKI_LINK -> new OpenCloseGeneratingProvider {
          override def openTag(visitor: HtmlGenerator#HtmlGeneratingVisitor, s: String, astNode: ASTNode): Unit = {
            val linkText = s.substring(astNode.getStartOffset+2, astNode.getEndOffset-2)
            // TODO: label selection is kind of iffy.
            val labelStart = linkText.lastIndexOf('.')
            val label = if (labelStart > 0) linkText.substring(labelStart + 1) else linkText
            val buffer = new java.lang.StringBuilder
            DocumentationManagerUtil.createHyperlink(buffer, linkText, label, false)
            val html = buffer.toString
            visitor.consumeHtml(html)
          }

          override def closeTag(visitor: HtmlGenerator#HtmlGeneratingVisitor, s: String, astNode: ASTNode): Unit = {}
        },
      ).asJava
    )

    parent
  }

  override def createInlinesLexer(): MarkdownLexer = super.createInlinesLexer()
}

object ScalaDocMarkdownFlavour {
  def withLanguageSyntaxHighlighting(project: Project): ScalaDocMarkdownFlavour = {
    val htmlSyntaxHighlighter = new HtmlSyntaxHighlighter {
      override def color(language: String, rawContent: String): HtmlChunk =
        HtmlSyntaxHighlighterCompanionProxy.colorHtmlChunk(project, selectLanguage(language), rawContent.stripLineEnd)

      private def selectLanguage(language: String): Language = {
        Language.getRegisteredLanguages.asScala
          .find(registeredLanguage => Option(language).exists(_.toLowerCase == registeredLanguage.getID.toLowerCase))
          .getOrElse(Scala3Language.INSTANCE)
      }
    }

    new ScalaDocMarkdownFlavour {
      override def createHtmlGeneratingProviders(linkMap: LinkMap, uri: URI): java.util.Map[IElementType, GeneratingProvider] = {
        val parent = super.createHtmlGeneratingProviders(linkMap, uri)
        parent.put(MarkdownElementTypes.CODE_FENCE, new CodeFenceSyntaxHighlighterGeneratingProvider(htmlSyntaxHighlighter))
        parent
      }
    }
  }

  /**
   * Information about a tag on a line. All positions are relative to the start of the line.
   *
   * @param start the start of the tag, including the @
   * @param end the end of the tag itself
   * @param argument if it exists, the start and end of the argument
   */
  case class TagInfo(start: Int, end: Int, argument: Option[(Int, Int)]) {
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
        nextMatchingChar(currentLine, tagEnd, c => !Character.isWhitespace(c)).map(argStart => {
          if (tag == MyScaladocParsing.TagNames.Define) {
            if (currentLine.charAt(argStart) == '{') {
              val argEnd = nextMatchingChar(currentLine, argStart, _ == '}')
                .map(_ + 1)
                .getOrElse(currentLine.length)
              (argStart, argEnd)
            } else {
              val argEnd = nextMatchingChar(currentLine, argStart, !Character.isLetterOrDigit(_)).getOrElse(currentLine.length)
              (argStart, argEnd)
            }
          } else {
            if (currentLine.charAt(argStart) == '`') {
              val argEnd = nextMatchingChar(currentLine, argStart+1, _ == '`')
                .map(_ + 1)
                .getOrElse(currentLine.length)
              (argStart, argEnd)
            } else {
              val argEnd = nextMatchingChar(currentLine, argStart, Character.isWhitespace).getOrElse(currentLine.length)
              (argStart, argEnd)
            }
          }
        })
      }.flatten

      Some(TagInfo(start, tagEnd, argument))
    } else None
  }

  private def nextMatchingChar(line: CharSequence, from: Int, predicate: Char => Boolean): Option[Int] = {
    (from until line.length)
      .find(i => predicate(line.charAt(i)))
  }
}