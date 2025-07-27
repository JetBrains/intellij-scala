package org.jetbrains.plugins.scala.lang.scaladoc.parser.parsing

import com.intellij.lang.PsiBuilder
import com.intellij.openapi.util.Key
import com.intellij.psi.tree.IElementType
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.parser.MarkdownParser
import org.intellij.markdown.{MarkdownElementTypes, MarkdownTokenTypes}
import org.jetbrains.plugins.scala.lang.scaladoc.lexer.ScalaDocTokenType
import org.jetbrains.plugins.scala.lang.scaladoc.parser.ScalaDocElementTypes
import org.jetbrains.plugins.scala.lang.scaladoc.parser.parsing.ScaladocMarkdownParsing.MARKDOWN_DATA
import org.jetbrains.plugins.scala.lang.scaladoc.parser.parsing.markdown.{ScalaDocMarkdownFlavour, ScalaDocTagMarkerBlock}

class ScaladocMarkdownParsing(private val builder: PsiBuilder,
                        private val tabSize: Int) extends ScalaDocElementTypes {
  private def splitContext(input: CharSequence): (String, List[Int]) = {
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
    val (lines, map) = result.toList.unzip

    (lines.mkString, map.appended(extraRemoved))
  }

  def parse(root: IElementType): Unit = {
    val rootMarker = builder.mark()
    val (content, map) = splitContext(builder.getOriginalText)

    if (content.isEmpty) return

    val out = new MarkdownParser(new ScalaDocMarkdownFlavour).parse(MarkdownElementTypes.MARKDOWN_FILE, content, true)

    // Place data needed for HTML in the builder for fetch after parsing
    builder.putUserData(MARKDOWN_DATA, (content, out))

    var currLine = 0

    def ensureBuilderInPosition(position: Int): Unit = {
      val target = position + map(currLine)

      if (builder.getCurrentOffset >= target) return

      val marker = builder.mark()
      while (builder.getCurrentOffset < target) builder.advanceLexer()

      marker.collapse(ScalaDocTokenType.DOC_COMMENT_DATA)
    }

    def advanceToNextLine(): Unit = {
      val whitespaceMarker = builder.mark()
      while (
        builder.getTokenType != ScalaDocTokenType.DOC_COMMENT_LEADING_ASTERISKS &&
          !builder.eof()
      ) builder.advanceLexer()

      whitespaceMarker.collapse(ScalaDocTokenType.DOC_WHITESPACE)

      // Skip the leading asterisk
      builder.advanceLexer()
    }

    def visitNode(node: ASTNode): Unit = {
      val tpe = node.getType

      if (tpe == MarkdownTokenTypes.EOL) {
        currLine += 1

        ensureBuilderInPosition(node.getStartOffset)
        advanceToNextLine()
      }

      // TODO: we need to special-case EMPH and STRONG to make them join their borders as a single node
      val element = tpe match {
        // ScalaDoc stuff
        case ScalaDocTagMarkerBlock.TAG_BLOCK => ScalaDocElementTypes.DOC_TAG
        case ScalaDocTagMarkerBlock.TAG_NAME => ScalaDocTokenType.DOC_TAG_NAME

        // Common blocks
        case MarkdownElementTypes.PARAGRAPH => ScalaDocElementTypes.DOC_PARAGRAPH
        case MarkdownElementTypes.CODE_FENCE => ScalaDocElementTypes.DOC_CODEBLOCK

        // Common inline tags
        case MarkdownElementTypes.EMPH => ScalaDocTokenType.DOC_ITALIC_TAG // NOTE: Distinct from MarkdownTokenTypes.EMPH, which is for the * character.
        case MarkdownElementTypes.STRONG => ScalaDocTokenType.DOC_BOLD_TAG
        case MarkdownElementTypes.CODE_SPAN => ScalaDocTokenType.DOC_MONOSPACE_TAG

        // Tokens
        case MarkdownTokenTypes.EMPH => ScalaDocTokenType.DOC_ITALIC_TAG
        case MarkdownTokenTypes.BACKTICK => ScalaDocTokenType.DOC_MONOSPACE_TAG

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
      } else {
        ensureBuilderInPosition(node.getStartOffset)

        val marker = builder.mark()
        node.getChildren.forEach(visitNode)

        ensureBuilderInPosition(node.getEndOffset)
        marker.done(element)
      }
    }

    visitNode(out)

    while (!builder.eof()) builder.advanceLexer()

    rootMarker.done(root)
  }
}

object ScaladocMarkdownParsing {
  val MARKDOWN_DATA: Key[(String, ASTNode)] = Key.create("scaladoc.markdown")
}
