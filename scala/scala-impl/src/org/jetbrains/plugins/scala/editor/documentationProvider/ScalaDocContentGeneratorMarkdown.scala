package org.jetbrains.plugins.scala.editor.documentationProvider

import org.intellij.markdown.ast.{ASTNode, CompositeASTNode}
import org.intellij.markdown.html.{GeneratingProvider, HtmlGenerator, HtmlGeneratorKt, TrimmingInlineHolderProvider}
import org.intellij.markdown.parser.LinkMap
import org.intellij.markdown.{IElementType, MarkdownElementTypes}
import org.jetbrains.plugins.scala.lang.scaladoc.parser.parsing.ScaladocMarkdownParsing
import org.jetbrains.plugins.scala.lang.scaladoc.parser.parsing.markdown.{ScalaDocMarkdownFlavour, ScalaDocTagMarkerBlock}
import org.jetbrains.plugins.scala.lang.scaladoc.psi.api.{ScDocComment, ScDocTag}

import java.net.URI
import scala.jdk.CollectionConverters._

class ScalaDocContentGeneratorMarkdown(comment: ScDocComment) extends ScalaDocContentGenerator {
  private val (markdown, markdownTree) = comment.getFirstChild.getUserData(ScaladocMarkdownParsing.MARKDOWN_DATA)
  private val lookupTag = {
    comment.tags
      .zip(markdownTree.getChildren.asScala.filter(_.getType == ScalaDocTagMarkerBlock.TAG_BLOCK))
      .toMap
  }
  private val markdownFlavour = new ScalaDocMarkdownFlavour.WithScalaSyntaxHighlighting(comment.getProject)
  /**
   * Inside a tag, we don't want the first paragraph to be wrapped into a p-tag
   * (which is the default behaviour and correct for other paragraphs).
   * Otherwise, the text will be moved to the next line.
   */
  private val markdownFlavourForTags = new ScalaDocMarkdownFlavour.WithScalaSyntaxHighlighting(comment.getProject) {
    override def createHtmlGeneratingProviders(linkMap: LinkMap, uri: URI): java.util.Map[IElementType, GeneratingProvider] = {
      val parent = super.createHtmlGeneratingProviders(linkMap, uri)
      parent.put(
        MarkdownElementTypes.PARAGRAPH,
        new TrimmingInlineHolderProvider {
          private var firstParagraph = true

          override def processNode(visitor: HtmlGenerator#HtmlGeneratingVisitor, text: String, node: ASTNode): Unit = {
            val isFirst = firstParagraph
            firstParagraph = false
            if (isFirst) {
              super.processNode(visitor, text, node)
            } else {
              visitor.consumeTagOpen(node, "p", Array.empty, false)
              super.processNode(visitor, text, node)
              visitor.consumeTagClose("p")
            }
          }
        }
      )
      parent
    }
  }

  override def appendTagDescriptionText(buffer: StringBuilder, tag: ScDocTag): Unit = {
    lookupTag.get(tag) match {
      case Some(markdownNode) =>
        val html = new HtmlGenerator(
          markdown,
          markdownNode,
          markdownFlavourForTags,
          false
        ).generateHtml(new HtmlGenerator.DefaultTagRenderer(HtmlGeneratorKt.getDUMMY_ATTRIBUTES_CUSTOMIZER, false))
        buffer.append(html)
      case None => ??? // TODO: this "should" be impossible, so it should throw an error, but it's technically possible
    }
  }

  override def appendDescriptionParts(buffer: StringBuilder, comment: ScDocComment): Boolean = {
    // TODO: This is wrong if `comment` is a wikidoc comment.
    val (markdown, markdownTree) = comment.getFirstChild.getUserData(ScaladocMarkdownParsing.MARKDOWN_DATA)

    // Everything up to the first tag
    val children = markdownTree.getChildren.asScala
    val descriptionParts = children.takeWhile(_.getType != ScalaDocTagMarkerBlock.TAG_BLOCK).toList

    // No children, no HTML generated
    if (descriptionParts.isEmpty) return false

    val descriptionNode = new CompositeASTNode(MarkdownElementTypes.MARKDOWN_FILE, descriptionParts.asJava)

    val html = new HtmlGenerator(
      markdown,
      descriptionNode,
      markdownFlavour,
      false
    ).generateHtml(new HtmlGenerator.DefaultTagRenderer(HtmlGeneratorKt.getDUMMY_ATTRIBUTES_CUSTOMIZER, false))
    buffer.append(html.subSequence(6, html.length-7))

    // Return true
    true
  }
}
