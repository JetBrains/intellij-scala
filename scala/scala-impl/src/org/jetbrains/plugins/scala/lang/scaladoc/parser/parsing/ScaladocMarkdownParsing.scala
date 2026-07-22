package org.jetbrains.plugins.scala.lang.scaladoc.parser.parsing

import com.intellij.lang.PsiBuilder
import com.intellij.lang.impl.PsiBuilderAdapter
import com.intellij.openapi.util.Key
import com.intellij.psi.tree.IElementType
import org.intellij.markdown
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.flavours.gfm.{GFMElementTypes, GFMTokenTypes}
import org.intellij.markdown.parser.MarkdownParser
import org.intellij.markdown.{MarkdownElementType, MarkdownElementTypes, MarkdownTokenTypes}
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.scala.lang.scaladoc.lexer.ScalaDocTokenType
import org.jetbrains.plugins.scala.lang.scaladoc.lexer.docsyntax.ScalaDocSyntaxElementType
import org.jetbrains.plugins.scala.lang.scaladoc.parser.ScalaDocElementTypes
import org.jetbrains.plugins.scala.lang.scaladoc.parser.parsing.ScaladocMarkdownParsing.{MkBuilder, MkTreeIt}
import org.jetbrains.plugins.scala.lang.scaladoc.parser.parsing.markdown.{ScalaDocMarkdownFlavour, ScalaDocTagMarkerBlock, WikiLinkParser}

import java.{util => ju}
import scala.annotation.tailrec
import scala.collection.immutable.ArraySeq
import scala.jdk.CollectionConverters.ListHasAsScala

/**
 *
 * @note Regarding block quotes:
 *
 *       Block quotes are extremely annoying!
 *       In the Markdown tree there are MarkdownElementTypes.BLOCK_QUOTE and MarkdownTokenTypes.BLOCK_QUOTES.
 *       And Bonus: Whitespace tokens can also sometimes be blockquote tokens!
 *       The element types are fairly straight forward and do what you would expect.
 *       But the tokens can come in different forms:
 *       1. At the beginning of a quote element: be careful here, because it also consumes the following whitespace
 *          but only in the Markdown tree! on the token level the whitespace comes as a separate token.
 *       2. Within a paragraph after newline: These eat their preceding (!) whitespaces
 *       3. Before a nested quote element to pad the parent quote elements.
 *          And surprise motherfucker! Here they are whitespace tokens, because why the fuck not!
 *
 *       Note that _ScalaDocMarkdownLexer makes sure that quotes can only eat their preceding whitespaces
 *       and not their following. Also, they will not eat the whitespaces at the beginning of the line.
 */
private class ScaladocMarkdownParsing(builder: MkBuilder, content: String) extends ScalaDocElementTypes {
  import builder.ensureBuilderInPosition

  private val elementsHandlingInnerWs = Set(
    MarkdownElementTypes.CODE_FENCE,
    MarkdownElementTypes.ORDERED_LIST,
    MarkdownElementTypes.UNORDERED_LIST,
  )

  private val elementsHandlingPrevWs = Set(
    MarkdownElementTypes.PARAGRAPH,
    MarkdownElementTypes.ORDERED_LIST,
    MarkdownElementTypes.UNORDERED_LIST,
    MarkdownElementTypes.CODE_BLOCK,
    ScalaDocTagMarkerBlock.TAG_BLOCK,
  )

  private def nodeText(node: ASTNode): String =
    content.substring(node.getStartOffset, node.getEndOffset)

  private def isBlockquoteWhitespace(node: ASTNode): Boolean =
    node.getType == MarkdownTokenTypes.WHITE_SPACE &&
      nodeText(node).contains('>')

  private def afterLeadingAsteriskOrBlockquote(@Nullable ty: IElementType): Boolean = {
    ty == ScalaDocTokenType.DOC_COMMENT_LEADING_ASTERISKS || ty == ScalaDocTokenType.DOC_BLOCKQUOTE
  }

  def visitNode(treeIt: MkTreeIt): Unit = {
    assert(!treeIt.ended)
    val tpe = treeIt.currentNodeType

    if (tpe == MarkdownTokenTypes.EOL) {
      ensureBuilderInPosition(treeIt.currentStartOffset)
      val parentHandlesWs = treeIt.parent.exists(it => elementsHandlingInnerWs(it.currentNodeType))
      val nextNodeHandlesWs = treeIt.peek().forall(node => elementsHandlingPrevWs(node.getType))
      builder.advanceToNextLine(emitInitialWs = !parentHandlesWs && !nextNodeHandlesWs)
      return
    }

    val elementTy = mapType(treeIt, tpe) match {
      case Some(element) => element
      case None => return
    }

    tpe match {
      case MarkdownTokenTypes.CODE_FENCE_CONTENT =>
        // just make everything code content, including the initial ws
        ensureBuilderInPosition(treeIt.currentEndOffset, elementTy)
      case MarkdownTokenTypes.BLOCK_QUOTE =>
        // For some reason the blockquote token not only eats the > but also the preceding and following whitespaces
        // We can't do anything about the preceding ws, because they also belong to the token in the lexer...
        // The following ws, though, is its own token, so split that at least off
        //ensureBuilderInPosition(treeIt.currentStartOffset)
        assert(builder.getTokenType == ScalaDocTokenType.DOC_BLOCKQUOTE)
        builder.advanceLexer()
        def nextTreeElementIsWsOrEOLOrHandlesWs = treeIt.peek().exists { e =>
          val ty = e.getType
          ty == MarkdownTokenTypes.EOL || ty == MarkdownTokenTypes.WHITE_SPACE || elementsHandlingPrevWs.contains(ty)
        }
        if (builder.getTokenType == ScalaDocTokenType.DOC_WHITESPACE && !nextTreeElementIsWsOrEOLOrHandlesWs) {
          builder.advanceLexer()
        }
      case MarkdownTokenTypes.WHITE_SPACE =>
        // We will arrive here when the whitespace is either following the asterisk at the beginning of the line
        // or the whitespace will actually be a blockquote token that the Markdown parser produces to pad already opened quotes
        // we want to eat all blockquote tokens and whitespace tokens until the end of the Markdown token node
        def isQuoteOrWs = {
          val ty = builder.getTokenType
          ty == ScalaDocTokenType.DOC_BLOCKQUOTE ||
            ty == ScalaDocTokenType.DOC_WHITESPACE
        }

        while (isQuoteOrWs && builder.currentPositionInContent + builder.getTokenText.length <= treeIt.currentEndOffset) {
          builder.advanceLexer()
        }
      case _ if !treeIt.currentHasChildren =>
        ensureBuilderInPosition(treeIt.currentStartOffset)

        val marker = builder.mark()
        ensureBuilderInPosition(treeIt.currentEndOffset)
        marker.collapse(elementTy)
      case ScalaDocTagMarkerBlock.TAG_BLOCK => visitTagBlock(elementTy, treeIt)
      case MarkdownElementTypes.EMPH => visitBorderSyntaxElement(elementTy, treeIt, ScalaDocTokenType.DOC_ITALIC_TAG, ScalaDocTokenType.DOC_ITALIC_TAG, 1)
      case MarkdownElementTypes.STRONG => visitBorderSyntaxElement(elementTy, treeIt, ScalaDocTokenType.DOC_BOLD_TAG, ScalaDocTokenType.DOC_BOLD_TAG, 2)
      case MarkdownElementTypes.CODE_SPAN => visitBorderSyntaxElement(elementTy, treeIt, ScalaDocTokenType.DOC_MONOSPACE_TAG, ScalaDocTokenType.DOC_MONOSPACE_TAG, 1)
      case GFMElementTypes.STRIKETHROUGH =>
        // GFM strikethrough delimiters may be one or two tildes (`~text~` or `~~text~~`),
        // but scaladoc has no single strikethrough, so ignore it
        val borderNum = treeIt.currentChildren.asScala.iterator.takeWhile(_.getType == GFMTokenTypes.TILDE).size
        if (borderNum > 1) {
          visitBorderSyntaxElement(elementTy, treeIt, ScalaDocTokenType.DOC_STRIKETHROUGH_TAG, ScalaDocTokenType.DOC_STRIKETHROUGH_TAG, borderNum)
        } else {
          visitRest(treeIt.startIterateCurrentChildren())
        }
      case WikiLinkParser.WIKI_LINK => visitWikiDocLink(treeIt)
      case MarkdownElementTypes.CODE_FENCE => visitCodeFence(elementTy, treeIt)
      case MarkdownElementTypes.PARAGRAPH => visitParagraph(elementTy, treeIt)
      case MarkdownElementTypes.BLOCK_QUOTE => visitBlockQuote(elementTy, treeIt)
      case MarkdownElementTypes.ATX_1 | MarkdownElementTypes.ATX_2 | MarkdownElementTypes.ATX_3 |
           MarkdownElementTypes.ATX_4 | MarkdownElementTypes.ATX_5 | MarkdownElementTypes.ATX_6 =>
        visitHeading(elementTy, treeIt)
      case _ =>
        ensureBuilderInPosition(treeIt.currentStartOffset)

        val endOffset = treeIt.currentEndOffset
        val marker = builder.mark()
        visitRest(treeIt.startIterateCurrentChildren())
        ensureBuilderInPosition(endOffset)
        marker.done(elementTy)
    }
  }

  private def visitTagBlock(elementTy: IElementType, treeIt: MkTreeIt): Unit = {
    // TAG_BLOCK needs to be dealt with in this complicated way,
    // due to needing whitespace inserted in a few places (which is not necessary for the rest)
    // and some nodes being special
    val hasArgument = treeIt.currentChildren.asScala.indexWhere(_.getType == ScalaDocTagMarkerBlock.TAG_ARGUMENT) != -1
    builder.ensureBuilderInPositionLeavingLastWs(treeIt.currentStartOffset)
    val marker = builder.mark()
    val childIt = treeIt.startIterateCurrentChildren()

    // drop until finding the tag name
    // (every tag block has a tag name, so we don't have to check for ended)
    childIt.advanceUntil(ScalaDocTagMarkerBlock.TAG_NAME)
    ensureBuilderInPosition(childIt.currentStartOffset, ScalaDocTokenType.DOC_WHITESPACE)
    val isThrows = hasArgument && {
      val tagName = builder.content.substring(childIt.currentStartOffset + 1, childIt.currentEndOffset)
      tagName == MyScaladocParsing.TagNames.Throws
    }

    visitNode(childIt)
    childIt.advance()

    if (hasArgument) {
      childIt.advanceUntil(ScalaDocTagMarkerBlock.TAG_ARGUMENT)
      ensureBuilderInPosition(childIt.currentStartOffset, ScalaDocTokenType.DOC_WHITESPACE)

      // Disabled because `builder` is not the right thing to pass here, but I'm not sure how to do it nicely.
      if (isThrows) {
        val marker = builder.mark()
        ensureBuilderInPosition(childIt.currentEndOffset, ScalaDocElementTypes.SCALA_DOC_REFERENCE_LINK)
        marker.done(ScalaDocTokenType.DOC_TAG_VALUE_TOKEN)
      } else {
        visitNode(childIt)
      }
      childIt.advance()
    }
    val endOffset = treeIt.currentEndOffset
    visitRest(childIt)

    builder.ensureBuilderInPositionLeavingLastWs(endOffset)
    marker.done(elementTy)
  }

  // Elements that are started and ended by a fixed amount of border elements like
  //  - italic      *text*
  //  - bold       **text**
  //  - code-spans  `text` or ``text`` (multiple `` are still one child in the tree)
  //  - wiki-links [[text]]
  private def visitBorderSyntaxElement(elementTy: IElementType,
                                       treeIt: MkTreeIt,
                                       startTagType: ScalaDocSyntaxElementType,
                                       endTagType: ScalaDocSyntaxElementType,
                                       borderNum: Int,
                                       innerType: IElementType = null): Unit = {
    assert(treeIt.currentChildren.size() >= borderNum * 2)
    val childIt = treeIt.startIterateCurrentChildren()
    ensureBuilderInPosition(childIt.currentStartOffset)

    val marker = builder.mark()
    // We *know* there are at least borderNum*2 children here
    // Force the first borderNum children to be a tagType
    for (_ <- 1 until borderNum) {
      childIt.advance()
    }
    ensureBuilderInPosition(childIt.currentEndOffset, startTagType)
    childIt.advance()

    while(childIt.availableNodesOnLevel > borderNum) {
      if (innerType == null) {
        visitNode(childIt)
      }
      childIt.advance()
    }

    if (innerType == null)
      ensureBuilderInPosition(childIt.currentStartOffset)
    else
      ensureBuilderInPosition(childIt.currentStartOffset, innerType)
    childIt.dropRest()

    // Force the last children to be a tagType
    ensureBuilderInPosition(treeIt.currentEndOffset, endTagType)

    marker.done(elementTy)
  }

  private def visitWikiDocLink(treeIt: MkTreeIt): Unit = {
    val info = WikiLinkParser.ChildrenInfo(treeIt.current)
    val childIt = treeIt.startIterateCurrentChildren()
    ensureBuilderInPosition(treeIt.currentStartOffset)

    val marker = builder.mark()

    // the first child is [[, or [[[ or even more [[[
    // second child is part of the reference
    childIt.advance()
    val linkOffset = childIt.currentStartOffset
    val firstText = nodeText(childIt.current)
    val isHttpLink = firstText.startsWith("http:") || firstText.startsWith("https:")

    val (elementType, refType) =
      if (isHttpLink) (ScalaDocTokenType.DOC_HTTP_LINK_TAG, ScalaDocTokenType.DOC_HTTP_LINK_VALUE)
      else (ScalaDocTokenType.DOC_LINK_TAG, ScalaDocElementTypes.SCALA_DOC_REFERENCE_LINK)

    ensureBuilderInPosition(linkOffset, elementType) // mark [[
    // if we have a whitespace right after [[ we want to mark that whitespace as refType
    if (childIt.currentNodeType == MarkdownTokenTypes.WHITE_SPACE) {
      childIt.advance()
    } else {
      while (!childIt.ended && childIt.currentNodeType == MarkdownTokenTypes.TEXT) {
        childIt.advance()
      }
    }

    ensureBuilderInPosition(if (childIt.ended) treeIt.currentEndOffset else childIt.currentStartOffset, refType)

    if (builder.getTokenType == ScalaDocTokenType.DOC_WHITESPACE) {
      builder.advanceLexer()
    }

    while (childIt.availableNodesOnLevel > info.closingBracketTokenCount) {
      visitNode(childIt)
      childIt.advance()
    }

    if (!childIt.ended) {
      ensureBuilderInPosition(childIt.currentStartOffset)
      childIt.dropRest()
      ensureBuilderInPosition(treeIt.currentEndOffset, ScalaDocTokenType.DOC_LINK_CLOSE_TAG)
    } else {
      ensureBuilderInPosition(treeIt.currentEndOffset)
    }

    marker.done(elementType)
  }

  private def visitCodeFence(elementTy: IElementType, treeIt: MkTreeIt): Unit = {
    val childIt = treeIt.startIterateCurrentChildren()
    @tailrec
    def splitWsFromFenceBorder(wsType: IElementType): Unit = {
      builder.getTokenType match {
        case ScalaDocTokenType.DOC_WHITESPACE =>
          if (builder.rawLookup(1) == ScalaDocTokenType.DOC_BLOCKQUOTE) {
            builder.advanceLexer()
            splitWsFromFenceBorder(wsType)
          } else {
            builder.remapCurrentToken(wsType)
            builder.advanceLexer()
          }
        case ScalaDocTokenType.DOC_BLOCKQUOTE =>
          builder.advanceLexer()
          splitWsFromFenceBorder(wsType)
        case _ =>
      }
    }
    assert(childIt.currentNodeType == MarkdownTokenTypes.CODE_FENCE_START)

    splitWsFromFenceBorder(ScalaDocTokenType.DOC_WHITESPACE)
    val marker = builder.mark()
    ensureBuilderInPosition(childIt.currentEndOffset, ScalaDocTokenType.DOC_INNER_CODE_TAG)

    while (!childIt.ended) {
      if (childIt.currentNodeType == MarkdownTokenTypes.CODE_FENCE_END) {
        splitWsFromFenceBorder(ScalaDocTokenType.DOC_INNER_CODE)
        ensureBuilderInPosition(childIt.currentEndOffset, ScalaDocTokenType.DOC_INNER_CLOSE_CODE_TAG)
      } else if (childIt.currentNodeType == MarkdownTokenTypes.WHITE_SPACE) {
        // this is a special handling for the initial whitespaces in a quoted code block
        // Before fence content, the Markdown parser will produce a whitespace token that contains a quote followed by a whitespace
        // before the next token is the actual content.
        // We want the last whitespace to belong to the code content not the quote token.
        def isQuoteOrWsAndNextIsQuote = {
          val ty = builder.getTokenType
          ty == ScalaDocTokenType.DOC_BLOCKQUOTE ||
            ty == ScalaDocTokenType.DOC_WHITESPACE && builder.rawLookup(1) == ScalaDocTokenType.DOC_BLOCKQUOTE
        }
        while (isQuoteOrWsAndNextIsQuote) {
          builder.advanceLexer()
        }
      } else {
        visitNode(childIt)
      }
      childIt.advance()
    }

    marker.done(elementTy)
  }

  private def visitParagraph(elementTy: IElementType, treeIt: MkTreeIt): Unit = {
    builder.ensureBuilderInPositionLeavingLastWs(treeIt.currentStartOffset)

    val marker = builder.mark()
    val childIt = treeIt.startIterateCurrentChildren()

    if (!childIt.ended && childIt.currentNodeType == MarkdownTokenTypes.WHITE_SPACE) {
      ensureBuilderInPosition(childIt.currentEndOffset, ScalaDocTokenType.DOC_WHITESPACE)
      childIt.advance()
    } else {
      builder.ensureBuilderInPosition(childIt.currentStartOffset, ScalaDocTokenType.DOC_WHITESPACE)
    }

    while (!childIt.ended) {
      if (childIt.currentNodeType == MarkdownTokenTypes.WHITE_SPACE && childIt.availableNodesOnLevel == 1) {
        ensureBuilderInPosition(childIt.currentStartOffset)
        ensureBuilderInPosition(childIt.currentEndOffset, ScalaDocTokenType.DOC_WHITESPACE)
      } else {
        visitNode(childIt)
      }
      childIt.advance()
    }

    ensureBuilderInPosition(treeIt.currentEndOffset)

    // also eat eol and whitespaces of the parents
    @tailrec
    def eat(it: MkTreeIt): Unit = {
      // The Markdown parser classifies parts of the > > > of a nested quote as whitespace,
      // but we don't want to consume those in the preceding paragraph.
      it.peek() match {
        case Some(node) =>
          val ty = node.getType
          if ((ty == MarkdownTokenTypes.WHITE_SPACE || ty == MarkdownTokenTypes.EOL) && !isBlockquoteWhitespace(node)) {
            it.advance()
            visitNode(it)
            eat(it)
          }
        case _ =>
          it.advance()
          it.parent match {
            case Some(parent) =>
              eat(parent)
            case None =>
          }
      }
    }
    eat(treeIt)

    marker.done(elementTy)
  }

  private def visitBlockQuote(elementTy: IElementType, treeIt: MkTreeIt): Unit = {
    // what's different here to the default is that we don't want to ensure the position here
    // because the blockquote token will do that
    val marker = builder.mark()
    val endOffset = treeIt.currentEndOffset
    visitRest(treeIt.startIterateCurrentChildren())
    ensureBuilderInPosition(endOffset)
    marker.done(elementTy)
  }

  private def visitHeading(elementTy: IElementType, treeIt: MkTreeIt): Unit = {
    ensureBuilderInPosition(treeIt.currentStartOffset)

    val childIt = treeIt.startIterateCurrentChildren()

    if (childIt.currentNodeType == MarkdownTokenTypes.WHITE_SPACE) {
      // for some reason a header element also encloses the whitespace befor the #
      // so try to split it off here
      ensureBuilderInPosition(childIt.currentEndOffset, ScalaDocTokenType.DOC_WHITESPACE)
      childIt.advance()
    }

    val endOffset = treeIt.currentEndOffset
    val marker = builder.mark()
    visitRest(childIt)
    ensureBuilderInPosition(endOffset)
    marker.done(elementTy)
  }

  private def mapType(treeIt: MkTreeIt, tpe: markdown.IElementType): Option[IElementType] = Some(
    tpe match {
      // ScalaDoc stuff
      case ScalaDocTagMarkerBlock.TAG_BLOCK => ScalaDocElementTypes.DOC_TAG
      case ScalaDocTagMarkerBlock.TAG_NAME => ScalaDocTokenType.DOC_TAG_NAME
      case ScalaDocTagMarkerBlock.TAG_ARGUMENT =>
        // Special handling to match `MyScaladocParsing`.
        ensureBuilderInPosition(treeIt.currentStartOffset)

        val marker = builder.mark()
        ensureBuilderInPosition(treeIt.currentEndOffset, ScalaDocTokenType.DOC_TAG_VALUE_TOKEN)
        marker.done(ScalaDocTokenType.DOC_TAG_VALUE_TOKEN)
        return None

      // Common blocks
      case MarkdownElementTypes.PARAGRAPH => ScalaDocElementTypes.DOC_PARAGRAPH
      case GFMElementTypes.TABLE => ScalaDocElementTypes.DOC_PARAGRAPH
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
      case GFMElementTypes.STRIKETHROUGH => ScalaDocTokenType.DOC_STRIKETHROUGH_TAG
      case MarkdownElementTypes.CODE_SPAN => ScalaDocTokenType.DOC_MONOSPACE_TAG
      case WikiLinkParser.WIKI_LINK => ScalaDocTokenType.DOC_LINK_TAG
      case MarkdownElementTypes.AUTOLINK => ScalaDocTokenType.DOC_LINK_TAG
      case MarkdownElementTypes.LINK_DEFINITION => ScalaDocTokenType.DOC_LINK_TAG

      // Tokens
      //case MarkdownTokenTypes.EMPH => ScalaDocTokenType.DOC_ITALIC_TAG
      //case MarkdownTokenTypes.BACKTICK => ScalaDocTokenType.DOC_MONOSPACE_TAG
      case MarkdownTokenTypes.BLOCK_QUOTE if builder.getTokenType == ScalaDocTokenType.DOC_BLOCKQUOTE => ScalaDocTokenType.DOC_BLOCKQUOTE
      case MarkdownTokenTypes.WHITE_SPACE if builder.getTokenType == ScalaDocTokenType.DOC_BLOCKQUOTE && isBlockquoteWhitespace(treeIt.current) =>
        ScalaDocTokenType.DOC_WHITESPACE
      case MarkdownTokenTypes.WHITE_SPACE if afterLeadingAsteriskOrBlockquote(builder.rawLookup(-1)) =>
        ScalaDocTokenType.DOC_WHITESPACE

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
      case MarkdownTokenTypes.CODE_FENCE_CONTENT => ScalaDocTokenType.DOC_INNER_CODE

      case _ =>
        val childIt = treeIt.startIterateCurrentChildren()
        visitRest(childIt)
        return None
    }
  )

  private def visitRest(treeIt: MkTreeIt): Unit = {
    while (!treeIt.ended) {
      visitNode(treeIt)
      treeIt.advance()
    }
  }
}

object ScaladocMarkdownParsing {
  val MARKDOWN_DATA: Key[(String, ASTNode)] = Key.create("scaladoc.markdown")

  private val CodeBlockStartRegex =
    raw"""(.*?)(\{\{\{|```)([\s\S]*)""".r

  private val CodeBlockEndRegex =
    raw"""(.*?)(}}}|```)([\s\S]*)""".r

//  private val CodeBlockStartRegex =
//    raw"""(.*?)(```+|\{\{\{)([\s\S]*)""".r
//
  private val BeforeCodeBlockFenceIsBlankRegex =
    raw"""[>\s]*""".r

  private def isBlankBeforeFence(text: String): Boolean =
    BeforeCodeBlockFenceIsBlankRegex.matches(text)

  def parse(psiBuilder: PsiBuilder, root: IElementType): Unit = {
    val original = psiBuilder.getOriginalText
    val (content, lineOffsetMapping) = splitContext(original)
    val builder = new MkBuilder(psiBuilder, content, lineOffsetMapping)
    val mkRootNode = new MarkdownParser(new ScalaDocMarkdownFlavour).parse(MarkdownElementTypes.MARKDOWN_FILE, content, true)

    // Place data needed for HTML in the builder for fetch after parsing
    builder.putUserData(MARKDOWN_DATA, (content, mkRootNode))

    val rootMarker = builder.mark()

    if (builder.getTokenType == ScalaDocTokenType.DOC_COMMENT_START) {
      builder.advanceLexer()
    }
    val parsing = new ScaladocMarkdownParsing(builder, content)
    parsing.visitNode(MkTreeIt(mkRootNode))

    if (!builder.eof()) {
      val marker = builder.mark()
      while (!builder.eof()) builder.advanceLexer()
      marker.collapse(ScalaDocTokenType.DOC_COMMENT_END)
    }
    rootMarker.done(root)
  }

  private val contentAfterStar = raw"\*( )+\S".r
  private def splitContext(input: CharSequence): (String, Seq[Int]) = {
    val text = input.toString
    val initialOffset = if (text.startsWith("/*")) 2 else 0
    val content = text.substring(
      initialOffset,
      if (text.endsWith("*/")) text.length - 2 else text.length
    )
    val startsOnFirstLine = contentAfterStar.findPrefixOf(content).isDefined

    var extraRemoved = initialOffset
    var firstLine = true
    var isInCodeBlock: Boolean = false
    val processedLines = Seq.newBuilder[(String, Int)]
    content.linesWithSeparators.foreach(line => {
      val initialLength = line.length
      val trimmed = line.stripLeading

      val cleanedLine = if (startsOnFirstLine && !firstLine && trimmed.startsWith("*  ")) {
        trimmed.substring(3)
      } else if (trimmed.startsWith("* ")) {
        trimmed.substring(2)
      } else if (trimmed.startsWith("*")) {
        trimmed.substring(1)
      } else {
        trimmed
      }

      // Don't fully delete empty lines, keep the newline.
      val finalLine = if (cleanedLine.isEmpty) "\n" else cleanedLine

      extraRemoved += initialLength - finalLine.length

      // Process code blocks
      // See <scala3-repository>/scaladoc/src/dotty/tools/scaladoc/tasty/comments/Preparser.scala
      val processed = new StringBuilder

      def addFakeLine(): Unit = {
        processed.append('\n')
        processedLines.addOne((processed.toString(), extraRemoved))
        extraRemoved -= 1
        processed.clear()
      }

      @tailrec
      def process(rest: String): Unit =
        rest match {
          case CodeBlockStartRegex(before, fence, after) if !isInCodeBlock =>
            if (isBlankBeforeFence(before)) {
              isInCodeBlock = true
              if (fence == "```") {
                processed.append(rest)
              } else {
                processed.append(before) // blank, but maybe not empty
                processed.append(fence)  // fence == "{{{"
                if (after.isBlank) {
                  processed.append(after)
                } else {
                  addFakeLine()
                  process(after)
                }
              }
            } else {
              processed.append(before)
              addFakeLine()
              process(fence + after)
            }
          case CodeBlockEndRegex(before, fence, after) =>
            isInCodeBlock = false
            processed.append(before)
            if (!isBlankBeforeFence(before))
              addFakeLine()
            processed.append(fence)
            if (after.isBlank) {
              processed.append(after)
            } else {
              addFakeLine()
              process(after)
            }
          case _ =>
            processed.append(rest)
        }

      process(finalLine)

      firstLine = false
      processedLines.addOne((processed.toString(), extraRemoved))
    })

    // Technically not very efficient, but meh. We need to collect both at once.
    // A `collect` would work but would be more manual.
    val lines = new StringBuilder
    val map = ArraySeq.newBuilder[Int]

    processedLines.result().foreach { case (line, spacing) =>
      lines.append(line)
      map += spacing
    }
    map += extraRemoved

    (lines.result(), map.result())
  }

  private class MkBuilder(base: PsiBuilder, val content: String, val lineOffsetMapping: Seq[Int]) extends PsiBuilderAdapter(base) {
    private var curLine = 0

    def currentPositionInContent: Int = getCurrentOffset - lineOffsetMapping(curLine)

    def ensureBuilderInPosition(position: Int, iType: IElementType = null, splitWs: Boolean = false): Unit = {
      val target = position + lineOffsetMapping(curLine)

      if (getCurrentOffset >= target) return

      if (splitWs && getTokenType == ScalaDocTokenType.DOC_WHITESPACE) {
        advanceLexer()

        if (getCurrentOffset >= target) return
      }

      var onlyWs = true
      val marker = mark()
      do {
        onlyWs &&= getTokenType == ScalaDocTokenType.DOC_WHITESPACE
        advanceLexer()
      } while (getCurrentOffset < target)

      marker.collapse(
        if (iType != null) iType
        else if (onlyWs) ScalaDocTokenType.DOC_WHITESPACE
        else ScalaDocTokenType.DOC_COMMENT_DATA
      )
    }

    def ensureBuilderInPositionLeavingLastWs(position: Int, iType: IElementType = ScalaDocTokenType.DOC_COMMENT_DATA): Unit = {
      val target = position + lineOffsetMapping(curLine)

      def isAtPosition = getCurrentOffset >= target || (getTokenType == ScalaDocTokenType.DOC_WHITESPACE && rawTokenTypeStart(1) >= target)
      if (isAtPosition) return

      val marker = mark()
      do {
        advanceLexer()
      } while (!isAtPosition)

      marker.collapse(iType)
    }

    def advanceToNextLine(emitInitialWs: Boolean): Unit = {
      val whitespaceMarker = mark()
      var gotOne = false
      while (getTokenType == ScalaDocTokenType.DOC_WHITESPACE) {
        gotOne = true
        advanceLexer()
      }
      if (gotOne) whitespaceMarker.collapse(ScalaDocTokenType.DOC_WHITESPACE)
      else whitespaceMarker.drop()

      curLine += 1

      // Skip the leading asterisk
      if (getTokenType == ScalaDocTokenType.DOC_COMMENT_LEADING_ASTERISKS) {
        advanceLexer()
      }

      if (emitInitialWs && getTokenType == ScalaDocTokenType.DOC_WHITESPACE && !getTokenText.exists(_ == '\n')) {
        advanceLexer()
      }
    }
  }

  private final class MkTreeIt(private var idx: Int, list: ju.List[ASTNode], val parent: Option[MkTreeIt]) {
    private var processesChildren: Boolean = false

    def index: Int = idx
    def current: ASTNode = list.get(idx)
    def currentNodeType: org.intellij.markdown.IElementType = current.getType
    def currentStartOffset: Int = current.getStartOffset
    def currentEndOffset: Int = current.getEndOffset
    def currentChildren: ju.List[ASTNode] = current.getChildren
    def currentHasChildren: Boolean = !currentChildren.isEmpty
    // returns the available nodes including the current one
    def availableNodesOnLevel: Int = list.size() - idx

    def ended: Boolean = idx >= list.size()
    def advance(): Unit = {
      if (!ended) {
        assert(!processesChildren)
        idx += 1
        if (ended) {
          parent.foreach { parent =>
            assert(parent.processesChildren)
            parent.processesChildren = false
          }
        }
      }
    }

    def advanceUntil(tpe: MarkdownElementType): Unit = {
      while (!ended && currentNodeType != tpe) {
        advance()
      }
    }

    def peek(): Option[ASTNode] =
      if (idx + 1 < list.size()) Some(list.get(idx + 1))
      else None

    def dropRest(): Unit = {
      while (!ended) advance()
    }

    def startIterateCurrentChildren(): MkTreeIt = {
      startChildIt(idx)
    }

    def startChildIt(idx: Int): MkTreeIt = {
      assert(!processesChildren)
      val children = list.get(idx).getChildren
      if (!children.isEmpty) {
        processesChildren = true
      }
      new MkTreeIt(0, currentChildren, Some(this))
    }
  }

  private object MkTreeIt {
    def apply(node: ASTNode): MkTreeIt =
      new MkTreeIt(0, ju.Collections.singletonList(node), None)
  }
}
