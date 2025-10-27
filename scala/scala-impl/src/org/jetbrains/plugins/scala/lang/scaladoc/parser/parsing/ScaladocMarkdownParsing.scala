package org.jetbrains.plugins.scala.lang.scaladoc.parser.parsing

import com.intellij.lang.PsiBuilder
import com.intellij.lang.impl.PsiBuilderAdapter
import com.intellij.openapi.util.Key
import com.intellij.psi.tree.IElementType
import org.intellij.markdown
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.parser.MarkdownParser
import org.intellij.markdown.{MarkdownElementType, MarkdownElementTypes, MarkdownTokenTypes}
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilderImpl
import org.jetbrains.plugins.scala.lang.parser.parsing.types.StableIdForImport
import org.jetbrains.plugins.scala.lang.scaladoc.lexer.ScalaDocTokenType
import org.jetbrains.plugins.scala.lang.scaladoc.lexer.docsyntax.ScalaDocSyntaxElementType
import org.jetbrains.plugins.scala.lang.scaladoc.parser.ScalaDocElementTypes
import org.jetbrains.plugins.scala.lang.scaladoc.parser.parsing.ScaladocMarkdownParsing.{MkBuilder, MkTreeIt}
import org.jetbrains.plugins.scala.lang.scaladoc.parser.parsing.markdown.{ScalaDocMarkdownFlavour, ScalaDocTagMarkerBlock, WikiLinkParser}

import java.{util => ju}
import scala.collection.immutable.ArraySeq
import scala.jdk.CollectionConverters.ListHasAsScala


private class ScaladocMarkdownParsing(builder: MkBuilder) extends ScalaDocElementTypes {
  @inline
  private def advanceToNextLine(): Unit = builder.advanceToNextLine()
  @inline
  private def ensureBuilderInPosition(i: Int): Unit = builder.ensureBuilderInPosition(i)
  @inline
  private def ensureBuilderInPosition(i: Int, elementType: IElementType): Unit = builder.ensureBuilderInPosition(i, elementType)

  def visitNode(treeIt: MkTreeIt): Unit = {
    assert(!treeIt.ended)
    val tpe = treeIt.currentNodeType

    if (tpe == MarkdownTokenTypes.EOL) {
      ensureBuilderInPosition(treeIt.currentStartOffset)
      advanceToNextLine()
      return
    }

    val elementTy = mapType(treeIt, tpe) match {
      case Some(element) => element
      case None => return
    }

    tpe match {
      case _ if !treeIt.currentHasChildren =>
        ensureBuilderInPosition(treeIt.currentStartOffset)

        val marker = builder.mark()
        ensureBuilderInPosition(treeIt.currentEndOffset)
        marker.collapse(elementTy)
      case ScalaDocTagMarkerBlock.TAG_BLOCK => visitTagBlock(elementTy, treeIt)
      case MarkdownElementTypes.EMPH => visitBorderSyntaxElement(elementTy, treeIt, ScalaDocTokenType.DOC_ITALIC_TAG, ScalaDocTokenType.DOC_ITALIC_TAG, 1)
      case MarkdownElementTypes.STRONG => visitBorderSyntaxElement(elementTy, treeIt, ScalaDocTokenType.DOC_BOLD_TAG, ScalaDocTokenType.DOC_BOLD_TAG, 2)
      case MarkdownElementTypes.CODE_SPAN => visitBorderSyntaxElement(elementTy, treeIt, ScalaDocTokenType.DOC_MONOSPACE_TAG, ScalaDocTokenType.DOC_MONOSPACE_TAG, 1)
      case WikiLinkParser.WIKI_LINK => visitBorderSyntaxElement(elementTy, treeIt, ScalaDocTokenType.DOC_LINK_TAG, ScalaDocTokenType.DOC_LINK_CLOSE_TAG, 2, ScalaDocElementTypes.SCALA_DOC_REFERENCE_LINK)
      case MarkdownElementTypes.PARAGRAPH => visitParagraph(elementTy, treeIt)
      case _ =>
        ensureBuilderInPosition(treeIt.currentStartOffset)

        val marker = builder.mark()
        visitRest(treeIt.startIterateCurrentChildren())
        ensureBuilderInPosition(treeIt.currentEndOffset)
        marker.done(elementTy)
    }
  }

  def visitTagBlock(elementTy: IElementType, treeIt: MkTreeIt): Unit = {
    // TAG_BLOCK needs to be dealt with in this complicated way,
    // due to needing whitespace inserted in a few places (which is not necessary for the rest)
    // and some nodes being special
    val hasArgument = treeIt.currentChildren.asScala.indexWhere(_.getType == ScalaDocTagMarkerBlock.TAG_ARGUMENT) != -1
    ensureBuilderInPosition(treeIt.currentStartOffset)
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
        ensureBuilderInPosition(childIt.currentEndOffset, ScalaDocElementTypes.SCALA_DOC_REFERENCE_LINK)
      } else {
        visitNode(childIt)
      }
      childIt.advance()
    }
    visitRest(childIt)

    ensureBuilderInPosition(treeIt.currentEndOffset)
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

  private def visitParagraph(elementTy: IElementType, treeIt: MkTreeIt): Unit = {
    ensureBuilderInPosition(treeIt.currentStartOffset)

    val marker = builder.mark()
    val childIt = treeIt.startIterateCurrentChildren()

    if (!childIt.ended && childIt.currentNodeType == MarkdownTokenTypes.WHITE_SPACE) {
      ensureBuilderInPosition(childIt.currentEndOffset, ScalaDocTokenType.DOC_WHITESPACE)
      childIt.advance()
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
      case MarkdownTokenTypes.WHITE_SPACE if builder.rawLookup(-1) == ScalaDocTokenType.DOC_COMMENT_LEADING_ASTERISKS =>
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
    parsing.visitNode(MkTreeIt(mkRootNode))

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

  private final class MkTreeIt(@Nullable firstNode: ASTNode, rest: ju.ListIterator[ASTNode], len: Int, private var parent: Option[MkTreeIt]) {
    @Nullable
    private var node = firstNode
    private var processesChildren: Boolean = false
    
    def currentNodeType: org.intellij.markdown.IElementType = node.getType
    def currentStartOffset: Int = node.getStartOffset
    def currentEndOffset: Int = node.getEndOffset
    def currentChildren: ju.List[ASTNode] = node.getChildren
    def currentHasChildren: Boolean = !currentChildren.isEmpty
    // returns the available nodes including the current one
    def availableNodesOnLevel: Int = len - rest.previousIndex()

    def ended: Boolean = node == null
    def advance(): Unit = {
      if (rest.hasNext)
        node = rest.next()
      else {
        parent.foreach { parent =>
          assert(parent.processesChildren)
          parent.processesChildren = false
        }
        node = null
      }
    }

    def advanceUntil(tpe: MarkdownElementType): Unit = {
      while (!ended && currentNodeType != tpe) {
        advance()
      }
    }

    def dropRest(): Unit = {
      while (!ended) advance()
    }

    def startIterateCurrentChildren(): MkTreeIt = {
      assert(!processesChildren)
      val children = currentChildren
      val it = children.listIterator()
      if (it.hasNext) {
        processesChildren = true
        new MkTreeIt(it.next(), it, children.size(), Some(this))
      } else {
        new MkTreeIt(null, ju.Collections.emptyListIterator(), 0, Some(this))
      }
    }
  }

  private object MkTreeIt {
    def apply(node: ASTNode): MkTreeIt =
      new MkTreeIt(node, ju.Collections.emptyListIterator(), 1, None)
  }
}
