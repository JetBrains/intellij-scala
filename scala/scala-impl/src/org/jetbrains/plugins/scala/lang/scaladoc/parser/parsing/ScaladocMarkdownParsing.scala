package org.jetbrains.plugins.scala.lang.scaladoc.parser.parsing

import com.intellij.lang.{PsiBuilder, WhitespacesAndCommentsBinder}
import com.intellij.openapi.util.Key
import com.intellij.psi.tree.IElementType
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.lexer.MarkdownLexer
import org.intellij.markdown.parser.MarkdownParser
import org.intellij.markdown.{MarkdownElementTypes, MarkdownTokenTypes}
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilderImpl
import org.jetbrains.plugins.scala.lang.parser.parsing.types.StableId
import org.jetbrains.plugins.scala.lang.scaladoc.lexer.ScalaDocTokenType
import org.jetbrains.plugins.scala.lang.scaladoc.parser.ScalaDocElementTypes
import org.jetbrains.plugins.scala.lang.scaladoc.parser.parsing.ScaladocMarkdownParsing.MARKDOWN_DATA
import org.jetbrains.plugins.scala.lang.scaladoc.parser.parsing.markdown.{ScalaDocMarkdownFlavour, ScalaDocTagMarkerBlock}

import scala.collection.immutable.ArraySeq
import scala.jdk.CollectionConverters._

class ScaladocMarkdownParsing(private val builder: PsiBuilder,
                        private val tabSize: Int) extends ScalaDocElementTypes {
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

  def parse(root: IElementType): Unit = {
    val rootMarker = builder.mark()

    val (content, map) = splitContext(builder.getOriginalText)

    if (content.isEmpty) return

    val out = new MarkdownParser(new ScalaDocMarkdownFlavour).parse(MarkdownElementTypes.MARKDOWN_FILE, content, true)

    // Place data needed for HTML in the builder for fetch after parsing
    builder.putUserData(MARKDOWN_DATA, (content, out))

    var currLine = 0

    def ensureBuilderInPosition(position: Int, iType: IElementType = ScalaDocTokenType.DOC_COMMENT_DATA): Unit = {
      val target = position + map(currLine)

      if (builder.getCurrentOffset >= target) return

      val marker = builder.mark()
      while (builder.getCurrentOffset < target) builder.advanceLexer()

      marker.collapse(iType)
    }

    def advanceToNextLine(): Unit = {
      def isTokenStructural(@Nullable iElementType: IElementType): Boolean =
        iElementType == ScalaDocTokenType.DOC_COMMENT_END ||
          iElementType == ScalaDocTokenType.DOC_COMMENT_START ||
          iElementType == ScalaDocTokenType.DOC_COMMENT_LEADING_ASTERISKS

      if (!isTokenStructural(builder.getTokenType) &&
        !builder.eof()) {
        val whitespaceMarker = builder.mark()
        while (
          !isTokenStructural(builder.getTokenType) &&
            !builder.eof()
        ) builder.advanceLexer()

        whitespaceMarker.collapse(ScalaDocTokenType.DOC_WHITESPACE)
      }

      // Skip the leading asterisk
      if (builder.getTokenType == ScalaDocTokenType.DOC_COMMENT_LEADING_ASTERISKS) {
        builder.advanceLexer()
      }
    }

    def visitNode(node: ASTNode): Unit = {
      val tpe = node.getType

      if (tpe == MarkdownTokenTypes.EOL) {
        ensureBuilderInPosition(node.getStartOffset)

        currLine += 1
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
        case MarkdownElementTypes.LIST_ITEM => ScalaDocElementTypes.DOC_LIST_ITEM
        // TODO: Unsure if it's iffy to use the same element type for both.
        case MarkdownElementTypes.UNORDERED_LIST => ScalaDocElementTypes.DOC_LIST
        case MarkdownElementTypes.ORDERED_LIST => ScalaDocElementTypes.DOC_LIST

        // Common inline tags
        case MarkdownElementTypes.EMPH => ScalaDocTokenType.DOC_ITALIC_TAG // NOTE: Distinct from MarkdownTokenTypes.EMPH, which is for the * character.
        case MarkdownElementTypes.STRONG => ScalaDocTokenType.DOC_BOLD_TAG
        case MarkdownElementTypes.CODE_SPAN => ScalaDocTokenType.DOC_MONOSPACE_TAG
        case MarkdownElementTypes.AUTOLINK => ScalaDocTokenType.DOC_LINK_TAG
        case MarkdownElementTypes.LINK_DEFINITION => ScalaDocTokenType.DOC_LINK_TAG

        // Tokens
        case MarkdownTokenTypes.EMPH => ScalaDocTokenType.DOC_ITALIC_TAG
        case MarkdownTokenTypes.BACKTICK => ScalaDocTokenType.DOC_MONOSPACE_TAG
        case MarkdownTokenTypes.WHITE_SPACE if builder.rawLookup(-1) == ScalaDocTokenType.DOC_COMMENT_LEADING_ASTERISKS
          => ScalaDocTokenType.DOC_WHITESPACE

        // Remains
        case MarkdownElementTypes.ATX_1 => ScalaDocTokenType.VALID_DOC_HEADER
        case MarkdownElementTypes.ATX_2 => ScalaDocTokenType.VALID_DOC_HEADER
        case MarkdownElementTypes.ATX_3 => ScalaDocTokenType.VALID_DOC_HEADER
        case MarkdownElementTypes.ATX_4 => ScalaDocTokenType.VALID_DOC_HEADER
        case MarkdownElementTypes.ATX_5 => ScalaDocTokenType.VALID_DOC_HEADER
        case MarkdownElementTypes.ATX_6 => ScalaDocTokenType.VALID_DOC_HEADER
        case MarkdownElementTypes.SETEXT_1 => ScalaDocTokenType.VALID_DOC_HEADER
        case MarkdownElementTypes.SETEXT_2 => ScalaDocTokenType.VALID_DOC_HEADER
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
          /*if (
            content.substring(children(name).getStartOffset + 1, children(name).getEndOffset)
              == MyScaladocParsing.TagNames.Throws) {
            val psiBuilder = mkScalaPsiBuilder(builder, isScala3 = false)
            // Taken from MyScaladocParsing.
            // I don't actually know how this works, and I'll probably forget to remove this comment
            StableId(ScalaDocTokenType.DOC_TAG_VALUE_TOKEN, forImport = true)(psiBuilder)
            // Skip forward in the builder
            skipTo(children(argument).getEndOffset)
          } else { */
            visitNode(children(argument))
          // }

          argument
        } else { name }
        children.drop(skippable + 1).foreach(visitNode)


        ensureBuilderInPosition(node.getEndOffset)
        marker.done(element)
      } else if (node.getType == MarkdownElementTypes.STRONG) {
        // Special casing for bold to merge boundary tokens
        val children = node.getChildren.asScala

        ensureBuilderInPosition(node.getStartOffset)
        val marker = builder.mark()
        // We *know* there are at least 4 children here
        // Force the first 2 children to be a DOC_BOLD_TAG
        ensureBuilderInPosition(children(1).getEndOffset, ScalaDocTokenType.DOC_BOLD_TAG)

        children.drop(2).dropRight(2).foreach(visitNode)

        ensureBuilderInPosition(children(children.length-2).getStartOffset)

        // Force the last 2 children to be a DOC_BOLD_TAG
        ensureBuilderInPosition(node.getEndOffset, ScalaDocTokenType.DOC_BOLD_TAG)

        marker.done(element)
      } else if (node.getType == MarkdownElementTypes.PARAGRAPH) {
        ensureBuilderInPosition(node.getStartOffset)

        val marker = builder.mark()

        val children = node.getChildren.asScala
        // Compat with wikidoc
        if (node.getChildren.get(0).getType == MarkdownTokenTypes.WHITE_SPACE) {
          ensureBuilderInPosition(node.getChildren.get(0).getEndOffset, ScalaDocTokenType.DOC_WHITESPACE)
          children.drop(1).foreach(visitNode)
        } else {
          children.foreach(visitNode)
        }

        ensureBuilderInPosition(node.getEndOffset)
        // TODO maybe: absorb whitespace/leading asterisks here too? Unsure.
        marker.done(element)
      } else { // TODO: Process wiki links separately as well.
        ensureBuilderInPosition(node.getStartOffset)

        val marker = builder.mark()
        node.getChildren.forEach(visitNode)

        ensureBuilderInPosition(node.getEndOffset)
        marker.done(element)
      }
    }

    ensureBuilderInPosition(out.getStartOffset, ScalaDocTokenType.DOC_COMMENT_START)
    visitNode(out)

    val marker = builder.mark()
    while (!builder.eof()) builder.advanceLexer()
    marker.collapse(ScalaDocTokenType.DOC_COMMENT_END)

    rootMarker.done(root)
  }

  def mkScalaPsiBuilder(delegate: PsiBuilder, isScala3: Boolean) =
    new ScalaPsiBuilderImpl(delegate, isScala3)
}

object ScaladocMarkdownParsing {
  val MARKDOWN_DATA: Key[(String, ASTNode)] = Key.create("scaladoc.markdown")
}
