package org.jetbrains.plugins.scala.lang.scaladoc.parser.parsing.markdown

import com.intellij.codeInsight.documentation.DocumentationManagerUtil
import com.intellij.lang.Language
import com.intellij.markdown.utils.CodeFenceSyntaxHighlighterGeneratingProvider
import com.intellij.markdown.utils.lang.HtmlSyntaxHighlighter
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.HtmlChunk
import org.intellij.markdown.ast.{ASTNode, ASTUtilKt}
import org.intellij.markdown.flavours.gfm.{GFMElementTypes, GFMFlavourDescriptor, GFMTokenTypes, StrikeThroughDelimiterParser}
import org.intellij.markdown.html._
import org.intellij.markdown.lexer.MarkdownLexer
import org.intellij.markdown.parser.sequentialparsers.impl._
import org.intellij.markdown.parser.sequentialparsers.{EmphasisLikeParser, SequentialParser, SequentialParserManager}
import org.intellij.markdown.parser.{LinkMap, LookaheadText, MarkerProcessorFactory, ProductionHolder}
import org.intellij.markdown.{IElementType, MarkdownElementTypes, MarkdownTokenTypes}
import org.jetbrains.plugins.scala.Scala3Language
import org.jetbrains.plugins.scala.editor.documentationProvider.HtmlPsiUtils
import org.jetbrains.plugins.scala.extensions.CharSeqExt
import org.jetbrains.plugins.scala.lang.scaladoc.lexer.markdown._CustomGFMLexer
import org.jetbrains.plugins.scala.lang.scaladoc.parser.parsing.MyScaladocParsing

import java.net.URI
import java.util
import scala.jdk.CollectionConverters._

class ScalaDocMarkdownFlavour extends GFMFlavourDescriptor {
  override def getMarkerProcessorFactory: MarkerProcessorFactory =
    (productionHolder: ProductionHolder) => {
      new ScalaDocMarkerProcessor(productionHolder, ScalaDocMarkdownConstraints.BASE)
    }

  private val sequentialParserManager: SequentialParserManager = new SequentialParserManager {
    override def getParserSequence: util.List[SequentialParser] = util.List.of(
      new AutolinkParser(util.List.of(MarkdownTokenTypes.AUTOLINK)),
      new BacktickParser(),
      new ImageParser(),
      new WikiLinkParser(),
      new InlineLinkParser(),
      new ReferenceLinkParser(),
      new EmphasisLikeParser(new EmphStrongDelimiterParser(), new StrikeThroughDelimiterParser()),
    )
  }

  override def getSequentialParserManager: SequentialParserManager = sequentialParserManager

  private val wsRegex = raw"\s+".r
  override def createHtmlGeneratingProviders(linkMap: LinkMap, uri: URI): java.util.Map[IElementType, GeneratingProvider] = {
    val parent = super.createHtmlGeneratingProviders(linkMap, uri)
    parent.putAll(
      Map(
        // ScalaDoc tags
        ScalaDocTagMarkerBlock.TAG_BLOCK -> new SimpleTagProvider("span"),
        ScalaDocTagMarkerBlock.TAG_NAME -> new SimpleTagProvider("span"),
        ScalaDocTagMarkerBlock.TAG_ARGUMENT -> new SimpleTagProvider("span"),

        WikiLinkParser.WIKI_LINK -> new OpenCloseGeneratingProvider {
          override def openTag(visitor: HtmlGenerator#HtmlGeneratingVisitor, s: String, astNode: ASTNode): Unit = {
            val children = astNode.getChildren
            val info = WikiLinkParser.ChildrenInfo(astNode)
            def text(i: (Int, Int)): String = {
              s.substring(children.get(i._1).getStartOffset, children.get(i._2).getEndOffset)
            }

            val refText = info.refTokens.fold("")(text)
            val description = info.descriptionTokens.fold(refText)(text)

            val html =
              if (refText.startsWith("http:") || refText.startsWith("https:")) {
                HtmlPsiUtils.hyperLink(refText, description)
              } else {
                val buffer = new java.lang.StringBuilder
                DocumentationManagerUtil.createHyperlink(buffer, refText, description, false)
                buffer.toString
              }
            visitor.consumeHtml(html)
          }

          override def closeTag(visitor: HtmlGenerator#HtmlGeneratingVisitor, s: String, astNode: ASTNode): Unit = {}
        },

        // GFM strikethrough may use one or two tildes (`~text~` or `~~text~~`), but scaladoc has
        // no single-tilde strikethrough. A single tilde must be rendered literally, and the border
        // for a real (double-tilde) strikethrough is not fixed, so trim the actual tilde run rather
        // than assuming two (otherwise `childrenToRender` computes an invalid sublist range, SCL-25712).
        GFMElementTypes.STRIKETHROUGH -> new SimpleInlineTagProvider("strike", 0, 0) {
          private def borderLen(node: ASTNode): Int =
            node.getChildren.asScala.iterator.takeWhile(_.getType == GFMTokenTypes.TILDE).size

          override def openTag(visitor: HtmlGenerator#HtmlGeneratingVisitor, text: String, node: ASTNode): Unit =
            if (borderLen(node) > 1) super.openTag(visitor, text, node)

          override def closeTag(visitor: HtmlGenerator#HtmlGeneratingVisitor, text: String, node: ASTNode): Unit =
            if (borderLen(node) > 1) super.closeTag(visitor, text, node)

          override def childrenToRender(node: ASTNode): java.util.List[ASTNode] = {
            val children = node.getChildren
            val border = borderLen(node)
            // For a single tilde, render everything (including the tildes) as plain text.
            if (border > 1) children.subList(border, children.size - border) else children
          }
        }
      ).asJava
    )

    val oldGFMAutolinkProvider = parent.get(GFMTokenTypes.GFM_AUTOLINK).ensuring(_ != null)
    parent.put(
      GFMTokenTypes.GFM_AUTOLINK,
      (visitor: HtmlGenerator#HtmlGeneratingVisitor, text: String, node: ASTNode) => {
        if (ASTUtilKt.getParentOfType(node, WikiLinkParser.WIKI_LINK) == null) {
          oldGFMAutolinkProvider.processNode(visitor, text, node)
        }
      }
    )

    parent
  }

  override def createInlinesLexer(): MarkdownLexer = new MarkdownLexer(new _CustomGFMLexer)
}

object ScalaDocMarkdownFlavour {
  class ScalaHtmlSyntaxHighlighter(project: Project) extends HtmlSyntaxHighlighter {
    override def color(language: String, rawContent: String): HtmlChunk =
      HtmlSyntaxHighlighterCompanionProxy.colorHtmlChunk(project, selectLanguage(language), rawContent.stripLineEnd)

    private def selectLanguage(language: String): Language = {
      Language.getRegisteredLanguages.asScala
        .find(registeredLanguage => Option(language).exists(_.toLowerCase == registeredLanguage.getID.toLowerCase))
        .getOrElse(Scala3Language.INSTANCE)
    }
  }

  class WithScalaSyntaxHighlighting(project: Project) extends ScalaDocMarkdownFlavour {
    override def createHtmlGeneratingProviders(linkMap: LinkMap, uri: URI): java.util.Map[IElementType, GeneratingProvider] = {
      val parent = super.createHtmlGeneratingProviders(linkMap, uri)
      val highlighter = new ScalaHtmlSyntaxHighlighter(project)
      parent.put(MarkdownElementTypes.CODE_FENCE, new CodeFenceSyntaxHighlighterGeneratingProvider(highlighter))
      parent
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
            val argEnd = indexAfterWikiDocRef(currentLine, argStart)
            (argStart, argEnd)
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

  private def indexAfterWikiDocRef(line: CharSequence, i: Int, inTicks: Boolean = false): Int = {
    if (i >= line.length()) {
      i
    } else {
      val c = line.charAt(i)
      if (c == '`') {
        indexAfterWikiDocRef(line, i+1, inTicks = !inTicks)
      } else if (c.isWhitespace && !inTicks) {
        i
      } else if (c == '\\') {
        indexAfterWikiDocRef(line, Math.min(i+2, line.length()), inTicks)
      } else {
        indexAfterWikiDocRef(line, i+1, inTicks)
      }
    }
  }
}