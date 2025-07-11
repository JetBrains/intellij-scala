package org.jetbrains.plugins.scala.lang.scaladoc.parser.parsing

import com.intellij.lang.PsiBuilder
import com.intellij.psi.tree.IElementType
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.parser.{MarkdownParser, MarkerProcessor, ProductionHolder}
import org.intellij.markdown.{MarkdownElementType, MarkdownElementTypes, MarkdownTokenTypes, flavours}
import org.intellij.markdown.flavours.commonmark.CommonMarkFlavourDescriptor
import org.jetbrains.plugins.scala.lang.scaladoc.lexer.ScalaDocTokenType
import org.jetbrains.plugins.scala.lang.scaladoc.lexer.docsyntax.ScalaDocSyntaxElementType
import org.jetbrains.plugins.scala.lang.scaladoc.parser.ScalaDocElementTypes
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
        val charsToRemove = if (trimmed.startsWith(" ", 1)) 2 else 1
        trimmed.substring(charsToRemove)
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

    var currLine = 0

    def ensureBuilderInPosition(position: Int): Unit = {
      val target = position + map(currLine)

      if (builder.getCurrentOffset >= target) return

      // TODO: We actually don't wanna cover up leading asterisks/whitespace
      val marker = builder.mark()
      while (builder.getCurrentOffset < target) builder.advanceLexer()
      marker.collapse(ScalaDocTokenType.DOC_COMMENT_DATA)
    }

    def visitNode(node: ASTNode): Unit = {
      val tpe = node.getType

      if (tpe == MarkdownTokenTypes.EOL) {
        currLine += 1
      }

      if (node.getChildren.isEmpty) return

      ensureBuilderInPosition(node.getStartOffset)

      val marker = builder.mark()
      node.getChildren.forEach(visitNode)

      ensureBuilderInPosition(node.getEndOffset)

      tpe match {
        case MarkdownElementTypes.PARAGRAPH => marker.done(ScalaDocElementTypes.DOC_PARAGRAPH)
        case MarkdownElementTypes.CODE_FENCE => marker.done(ScalaDocElementTypes.DOC_CODEBLOCK)
        case MarkdownElementTypes.ATX_1 => marker.done(ScalaDocTokenType.VALID_DOC_HEADER)
        case MarkdownElementTypes.ATX_2 => marker.done(ScalaDocTokenType.VALID_DOC_HEADER)
        case MarkdownElementTypes.ATX_3 => marker.done(ScalaDocTokenType.VALID_DOC_HEADER)
        case MarkdownElementTypes.ATX_4 => marker.done(ScalaDocTokenType.VALID_DOC_HEADER)
        case MarkdownElementTypes.ATX_5 => marker.done(ScalaDocTokenType.VALID_DOC_HEADER)
        case MarkdownElementTypes.ATX_6 => marker.done(ScalaDocTokenType.VALID_DOC_HEADER)
//        case MarkdownElementTypes.AUTOLINK => marker.done()
//        case MarkdownElementTypes.BLOCK_QUOTE => marker.done()
        case MarkdownElementTypes.EMPH => marker.done(ScalaDocTokenType.DOC_ITALIC_TAG) // NOTE: Distinct from MarkdownTokenTypes.EMPH, which is for the * character.
        case MarkdownElementTypes.STRONG => marker.done(ScalaDocTokenType.DOC_BOLD_TAG)
        case MarkdownElementTypes.SETEXT_1 => marker.done(ScalaDocTokenType.VALID_DOC_HEADER)
        case MarkdownElementTypes.SETEXT_2 => marker.done(ScalaDocTokenType.VALID_DOC_HEADER)

//        case MarkdownTokenTypes.TEXT => marker.collapse(ScalaDocTokenType.DOC_COMMENT_DATA)

        case ScalaDocTagMarkerBlock.TAG_BLOCK => marker.done(ScalaDocElementTypes.DOC_TAG)
        case ScalaDocTagMarkerBlock.TAG_NAME => marker.done(ScalaDocTokenType.DOC_TAG_NAME)
        case _ => marker.drop()
      }
    }

    visitNode(out)

    while (!builder.eof()) builder.advanceLexer()

    rootMarker.done(root)
  }
}
