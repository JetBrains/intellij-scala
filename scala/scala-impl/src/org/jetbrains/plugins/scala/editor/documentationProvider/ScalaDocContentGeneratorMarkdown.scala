package org.jetbrains.plugins.scala.editor.documentationProvider

import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.ast.CompositeASTNode
import org.intellij.markdown.html.{HtmlGenerator, HtmlGeneratorKt}
import org.jetbrains.plugins.scala.lang.scaladoc.parser.parsing.ScaladocMarkdownParsing
import org.jetbrains.plugins.scala.lang.scaladoc.parser.parsing.markdown.{ScalaDocMarkdownFlavour, ScalaDocTagMarkerBlock}
import org.jetbrains.plugins.scala.lang.scaladoc.psi.api.{ScDocComment, ScDocTag}

import scala.jdk.CollectionConverters._

class ScalaDocContentGeneratorMarkdown(comment: ScDocComment) extends ScalaDocContentGenerator {
  private val (markdown, markdownTree) = comment.getFirstChild.getUserData(ScaladocMarkdownParsing.MARKDOWN_DATA)
  private val lookupTag = {
    comment.tags
      .zip(markdownTree.getChildren.asScala.filter(_.getType == ScalaDocTagMarkerBlock.TAG_BLOCK))
      .toMap
  }
  private val markdownFlavour = ScalaDocMarkdownFlavour.withLanguageSyntaxHighlighting(comment.getProject)

  override def appendTagDescriptionText(buffer: StringBuilder, tag: ScDocTag): Unit = {
    lookupTag.get(tag) match {
      case Some(markdownNode) =>
        val html = new HtmlGenerator(
          markdown,
          markdownNode,
          markdownFlavour,
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
