package org.jetbrains.plugins.scala.lang.scaladoc.parser.parsing

import com.intellij.lang.PsiBuilder
import com.intellij.lang.impl.PsiBuilderAdapter
import com.intellij.openapi.util.Key
import com.intellij.psi.tree.IElementType
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.parser.MarkdownParser
import org.intellij.markdown.{MarkdownElementTypes, MarkdownTokenTypes}
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilderImpl
import org.jetbrains.plugins.scala.lang.parser.parsing.types.StableIdForImport
import org.jetbrains.plugins.scala.lang.scaladoc.lexer.ScalaDocTokenType
import org.jetbrains.plugins.scala.lang.scaladoc.parser.ScalaDocElementTypes
import org.jetbrains.plugins.scala.lang.scaladoc.parser.parsing.ScaladocMarkdownParsing.MkBuilder
import org.jetbrains.plugins.scala.lang.scaladoc.parser.parsing.markdown.{ScalaDocMarkdownFlavour, ScalaDocTagMarkerBlock, WikiLinkParser}

import scala.collection.immutable.ArraySeq
import scala.jdk.CollectionConverters._

private class ScaladocMarkdownParsing(builder: MkBuilder) extends ScalaDocElementTypes {
  @inline
  private def advanceToNextLine(): Unit = builder.advanceToNextLine()
  @inline
  private def ensureBuilderInPosition(i: Int): Unit = builder.ensureBuilderInPosition(i)
  @inline
  private def ensureBuilderInPosition(i: Int, elementType: IElementType): Unit = builder.ensureBuilderInPosition(i, elementType)

  def visitNode(node: ASTNode): Unit = {
    val tpe = node.getType

    if (tpe == MarkdownTokenTypes.EOL) {
      ensureBuilderInPosition(node.getStartOffset)
      advanceToNextLine()
      return
    }

    val element = tpe match {
      // ScalaDoc stuff
      case ScalaDocTagMarkerBlock.TAG_BLOCK => ScalaDocElementTypes.DOC_TAG
      case ScalaDocTagMarkerBlock.TAG_NAME => ScalaDocTokenType.DOC_TAG_NAME
      case ScalaDocTagMarkerBlock.TAG_ARGUMENT =>
        // Special handling to match `MyScaladocParsing`.
        ensureBuilderInPosition(node.getStartOffset)

        val marker = builder.mark()
        ensureBuilderInPosition(node.getEndOffset, ScalaDocTokenType.DOC_TAG_VALUE_TOKEN)
        marker.done(ScalaDocTokenType.DOC_TAG_VALUE_TOKEN)
        return

      // Common blocks
      case MarkdownElementTypes.PARAGRAPH => ScalaDocElementTypes.DOC_PARAGRAPH
      case MarkdownElementTypes.CODE_FENCE => ScalaDocElementTypes.DOC_CODEBLOCK
      case MarkdownElementTypes.BLOCK_QUOTE => ScalaDocElementTypes.DOC_BLOCKQUOTE
      case MarkdownTokenTypes.LIST_NUMBER => ScalaDocTokenType.DOC_LIST_ITEM_HEAD
      case MarkdownTokenTypes.LIST_BULLET => ScalaDocTokenType.DOC_LIST_ITEM_HEAD
      case MarkdownElementTypes.LIST_ITEM => ScalaDocElementTypes.DOC_LIST_ITEM
      case MarkdownElementTypes.UNORDERED_LIST => ScalaDocElementTypes.DOC_LIST
      case MarkdownElementTypes.ORDERED_LIST => ScalaDocElementTypes.DOC_LIST

      // Common inline tags
      case MarkdownElementTypes.EMPH => ScalaDocTokenType.DOC_ITALIC_TAG // NOTE: Distinct from MarkdownTokenTypes.EMPH, which is for the * character.
      case MarkdownElementTypes.STRONG => ScalaDocTokenType.DOC_BOLD_TAG
      case MarkdownElementTypes.CODE_SPAN => ScalaDocTokenType.DOC_MONOSPACE_TAG
      case WikiLinkParser.WIKI_LINK => ScalaDocTokenType.DOC_LINK_TAG
      case MarkdownElementTypes.AUTOLINK => ScalaDocTokenType.DOC_LINK_TAG
      case MarkdownElementTypes.LINK_DEFINITION => ScalaDocTokenType.DOC_LINK_TAG

      // Tokens
      //case MarkdownTokenTypes.EMPH => ScalaDocTokenType.DOC_ITALIC_TAG
      //case MarkdownTokenTypes.BACKTICK => ScalaDocTokenType.DOC_MONOSPACE_TAG
      case MarkdownTokenTypes.WHITE_SPACE if builder.rawLookup(-1) == ScalaDocTokenType.DOC_COMMENT_LEADING_ASTERISKS
      => ScalaDocTokenType.DOC_WHITESPACE

      // Remains
      // Not needed: it is parsed as regular text by the parser!
      // case MarkdownElementTypes.HTML_BLOCK => ???
      case MarkdownElementTypes.ATX_1 => ScalaDocTokenType.DOC_MARKDOWN_HEADER
      case MarkdownElementTypes.ATX_2 => ScalaDocTokenType.DOC_MARKDOWN_HEADER
      case MarkdownElementTypes.ATX_3 => ScalaDocTokenType.DOC_MARKDOWN_HEADER
      case MarkdownElementTypes.ATX_4 => ScalaDocTokenType.DOC_MARKDOWN_HEADER
      case MarkdownElementTypes.ATX_5 => ScalaDocTokenType.DOC_MARKDOWN_HEADER
      case MarkdownElementTypes.ATX_6 => ScalaDocTokenType.DOC_MARKDOWN_HEADER
      case MarkdownElementTypes.SETEXT_1 => ScalaDocTokenType.DOC_MARKDOWN_HEADER
      case MarkdownElementTypes.SETEXT_2 => ScalaDocTokenType.DOC_MARKDOWN_HEADER

      case _ =>
        node.getChildren.forEach(visitNode)
        return
    }

    if (node.getChildren.isEmpty) {
      ensureBuilderInPosition(node.getStartOffset)

      val marker = builder.mark()
      ensureBuilderInPosition(node.getEndOffset)
      marker.collapse(element)
    } else if (node.getType == ScalaDocTagMarkerBlock.TAG_BLOCK) {
      // TAG_BLOCK needs to be dealt with in this complicated way,
      // due to needing whitespace inserted in a few places (which is not necessary for the rest)
      // and some nodes being special
      ensureBuilderInPosition(node.getStartOffset)

      val children = node.getChildren.asScala

      // Never -1; name always exists
      val name = children.indexWhere(_.getType == ScalaDocTagMarkerBlock.TAG_NAME)

      val marker = builder.mark()

      ensureBuilderInPosition(children(name).getStartOffset, ScalaDocTokenType.DOC_WHITESPACE)
      visitNode(children(name))

      val argument = children.indexWhere(_.getType == ScalaDocTagMarkerBlock.TAG_ARGUMENT)

      val skippable = if (argument != -1) {
        ensureBuilderInPosition(children(argument).getStartOffset, ScalaDocTokenType.DOC_WHITESPACE)

        // Disabled because `builder` is not the right thing to pass here, but I'm not sure how to do it nicely.
        if (
          builder.content.substring(children(name).getStartOffset + 1, children(name).getEndOffset)
            == MyScaladocParsing.TagNames.Throws
        ) {
          ensureBuilderInPosition(children(argument).getEndOffset, ScalaDocElementTypes.SCALA_DOC_REFERENCE_LINK)
        } else {
          visitNode(children(argument))
        }

        argument
      } else {
        name
      }
      children.drop(skippable + 1).foreach(visitNode)


      ensureBuilderInPosition(node.getEndOffset)
      marker.done(element)
    } else if (node.getType == MarkdownElementTypes.EMPH) {
      // Special casing for italic to set emph tokens
      val children = node.getChildren.asScala

      ensureBuilderInPosition(node.getStartOffset)
      val marker = builder.mark()
      // We *know* there are at least 2 children here
      // Force the first child to be a DOC_ITALIC_TAG
      ensureBuilderInPosition(children.head.getEndOffset, ScalaDocTokenType.DOC_ITALIC_TAG)

      children.slice(1, children.length - 1).foreach(visitNode)

      ensureBuilderInPosition(children.last.getStartOffset)

      // Force the last child to be a DOC_ITALIC_TAG
      ensureBuilderInPosition(node.getEndOffset, ScalaDocTokenType.DOC_ITALIC_TAG)

      marker.done(element)
    } else if (node.getType == MarkdownElementTypes.STRONG) {
      // Special casing for bold to merge boundary tokens
      val children = node.getChildren.asScala

      ensureBuilderInPosition(node.getStartOffset)
      val marker = builder.mark()
      // We *know* there are at least 4 children here
      // Force the first 2 children to be a DOC_BOLD_TAG
      ensureBuilderInPosition(children(1).getEndOffset, ScalaDocTokenType.DOC_BOLD_TAG)

      children.slice(2, children.length - 2).foreach(visitNode)

      ensureBuilderInPosition(children(children.length - 2).getStartOffset)

      // Force the last 2 children to be a DOC_BOLD_TAG
      ensureBuilderInPosition(node.getEndOffset, ScalaDocTokenType.DOC_BOLD_TAG)

      marker.done(element)
    } else if (node.getType == MarkdownElementTypes.CODE_SPAN) {
      // Special case for code spans
      // Even multiple backticks will occur as one MarkdownTokenTypes.BACKTICK token
      val children = node.getChildren.asScala

      ensureBuilderInPosition(node.getStartOffset)
      val marker = builder.mark()
      // We *know* there are at least 2 children here
      // Force the first child to be a DOC_MONOSPACE_TAG
      ensureBuilderInPosition(children.head.getEndOffset, ScalaDocTokenType.DOC_MONOSPACE_TAG)

      children.slice(1, children.length - 1).foreach(visitNode)

      ensureBuilderInPosition(children.last.getStartOffset)

      // Force the last child to be a DOC_MONOSPACE_TAG
      ensureBuilderInPosition(node.getEndOffset, ScalaDocTokenType.DOC_MONOSPACE_TAG)

      marker.done(element)
    } else if (node.getType == WikiLinkParser.WIKI_LINK) {
      // Special casing for bold to merge boundary tokens
      val children = node.getChildren.asScala

      ensureBuilderInPosition(node.getStartOffset)
      val marker = builder.mark()
      // We *know* there are at least 4 children here
      // Force the first 2 children to be a DOC_LINK_TAG
      ensureBuilderInPosition(children(1).getEndOffset, ScalaDocTokenType.DOC_LINK_TAG)

      ensureBuilderInPosition(children(children.length - 2).getStartOffset, ScalaDocElementTypes.SCALA_DOC_REFERENCE_LINK)

      // Force the last 2 children to be a DOC_LINK_CLOSE_TAG
      ensureBuilderInPosition(node.getEndOffset, ScalaDocTokenType.DOC_LINK_CLOSE_TAG)

      marker.done(element)
    } else if (node.getType == MarkdownElementTypes.PARAGRAPH) {
      ensureBuilderInPosition(node.getStartOffset)

      val marker = builder.mark()

      val children = node.getChildren.asScala.toSeq
      // Compat with wikidoc
      val initialWs = children.headOption.filter(_.getType == MarkdownTokenTypes.WHITE_SPACE)
      val restChildren = initialWs match {
        case Some(ws) =>
          ensureBuilderInPosition(ws.getEndOffset, ScalaDocTokenType.DOC_WHITESPACE)
          children.drop(1)
        case None =>
          children
      }

      val lastWs = restChildren.lastOption.filter(_.getType == MarkdownTokenTypes.WHITE_SPACE)
      lastWs match {
        case Some(ws) =>
          restChildren.dropRight(1).foreach(visitNode)
          ensureBuilderInPosition(ws.getStartOffset)
          ensureBuilderInPosition(ws.getEndOffset, ScalaDocTokenType.DOC_WHITESPACE)
        case None =>
          restChildren.foreach(visitNode)
      }

      ensureBuilderInPosition(node.getEndOffset)
      marker.done(element)
    } else {
      ensureBuilderInPosition(node.getStartOffset)

      val marker = builder.mark()
      node.getChildren.forEach(visitNode)

      ensureBuilderInPosition(node.getEndOffset)
      marker.done(element)
    }
  }
}

object ScaladocMarkdownParsing {
  val MARKDOWN_DATA: Key[(String, ASTNode)] = Key.create("scaladoc.markdown")

  def parse(psiBuilder: PsiBuilder, root: IElementType): Unit = {
    val original = psiBuilder.getOriginalText
    val (content, lineOffsetMapping) = splitContext(original)
    val builder = new MkBuilder(psiBuilder, content, lineOffsetMapping)
    val mkRootNode = new MarkdownParser(new ScalaDocMarkdownFlavour).parse(MarkdownElementTypes.MARKDOWN_FILE, content, true)

    // Place data needed for HTML in the builder for fetch after parsing
    builder.putUserData(MARKDOWN_DATA, (content, mkRootNode))

    val rootMarker = builder.mark()

    builder.ensureBuilderInPosition(mkRootNode.getStartOffset, ScalaDocTokenType.DOC_COMMENT_START)
    val parsing = new ScaladocMarkdownParsing(builder)
    parsing.visitNode(mkRootNode)

    if (!builder.eof()) {
      val marker = builder.mark()
      while (!builder.eof()) builder.advanceLexer()
      marker.collapse(ScalaDocTokenType.DOC_COMMENT_END)
    }
    rootMarker.done(root)
  }

  def parseCodeReference(psiBuilder: PsiBuilder): com.intellij.lang.ASTNode = {
    val marker = psiBuilder.mark()
    val scPsiBuilder = new ScalaPsiBuilderImpl(psiBuilder, true)
    StableIdForImport(ScalaDocTokenType.DOC_CODE_LINK_VALUE)(scPsiBuilder)
    while (!scPsiBuilder.eof()) scPsiBuilder.advanceLexer()
    marker.done(ScalaDocElementTypes.SCALA_DOC_REFERENCE_LINK)
    psiBuilder.getTreeBuilt
  }

  private def splitContext(input: CharSequence): (String, Seq[Int]) = {
    val text = input.toString
    val initialOffset = if (text.startsWith("/*")) 2 else 0

    val content = text.substring(
      initialOffset,
      if (text.endsWith("*/")) text.length - 2 else text.length
    )

    var extraRemoved = initialOffset
    val result = content.linesWithSeparators.map(line => {
      val initialLength = line.length
      val trimmed = line.stripLeading

      val cleanedLine = if (!trimmed.startsWith("*")) {
        trimmed
      } else {
        trimmed.substring(1)
      }

      // Don't fully delete empty lines, keep the newline.
      val finalLine = if (cleanedLine.isEmpty) "\n" else cleanedLine

      extraRemoved += initialLength - finalLine.length

      (finalLine, extraRemoved)
    })

    // Technically not very efficient, but meh. We need to collect both at once.
    // A `collect` would work but would be more manual.
    val lines = new StringBuilder
    val map = ArraySeq.newBuilder[Int]

    result.foreach { case (line, spacing) =>
      lines.append(line)
      map += spacing
    }
    map += extraRemoved

    (lines.result(), map.result())
  }

  private class MkBuilder(base: PsiBuilder, val content: String, val lineOffsetMapping: Seq[Int]) extends PsiBuilderAdapter(base) {
    private var curLine = 0

    def ensureBuilderInPosition(position: Int, iType: IElementType = ScalaDocTokenType.DOC_COMMENT_DATA): Unit = {
      val target = position + lineOffsetMapping(curLine)

      if (getCurrentOffset >= target) return

      val marker = mark()
      while (getCurrentOffset < target) advanceLexer()

      marker.collapse(iType)
    }

    def advanceToNextLine(): Unit = {
      val whitespaceMarker = mark()
      var gotOne = false
      while (getTokenType == ScalaDocTokenType.DOC_WHITESPACE) {
        gotOne = true
        curLine += getTokenText.count(_ == '\n')
        advanceLexer()
      }
      if (gotOne) whitespaceMarker.collapse(ScalaDocTokenType.DOC_WHITESPACE)
      else whitespaceMarker.drop()

      // Skip the leading asterisk
      if (getTokenType == ScalaDocTokenType.DOC_COMMENT_LEADING_ASTERISKS) {
        advanceLexer()
      }
    }
  }
}
