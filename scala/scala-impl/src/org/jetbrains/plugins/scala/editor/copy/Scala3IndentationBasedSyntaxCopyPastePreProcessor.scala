package org.jetbrains.plugins.scala.editor.copy

import com.intellij.application.options.CodeStyle
import com.intellij.codeInsight.editorActions.CopyPastePreProcessor
import com.intellij.openapi.editor.{Caret, Document, Editor, RawText}
import com.intellij.openapi.project.Project
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiElement, PsiErrorElement, PsiFile}
import org.apache.commons.lang3.StringUtils
import org.jetbrains.plugins.scala.editor.Scala3IndentationBasedSyntaxUtils.calcIndentationString
import org.jetbrains.plugins.scala.editor.ScalaEditorUtils.findElementAtCaret_WithFixedEOF
import org.jetbrains.plugins.scala.editor.copy.Scala3IndentationBasedSyntaxCopyPastePreProcessor._
import org.jetbrains.plugins.scala.extensions.{ObjectExt, PsiElementExt}
import org.jetbrains.plugins.scala.lang.TokenSets
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.base.ScOptionalBracesOwner
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScMember
import org.jetbrains.plugins.scala.lang.scaladoc.parser.ScalaDocElementTypes
import org.jetbrains.plugins.scala.settings.ScalaApplicationSettings
import org.jetbrains.plugins.scala.util.IndentUtil
import org.jetbrains.plugins.scala.{Scala3Language, ScalaBundle, ScalaFileType}

class Scala3IndentationBasedSyntaxCopyPastePreProcessor extends CopyPastePreProcessor {
  override def requiresAllDocumentsToBeCommitted(editor: Editor, project: Project): Boolean = false

  override def preprocessOnCopy(file: PsiFile, startOffsets: Array[Int], endOffsets: Array[Int], text: String): String =
    null

  // the formatter is always run on pasted snippets, so we just need to adjust indentation so that the formatter recognizes it
  // this only called on single caret, paste for multiple carets is handled as raw text
  override def preprocessOnPaste(project: Project, file: PsiFile, editor: Editor, text: String, rawText: RawText): String = {
    val isScala3File = file.is[ScalaFile] && file.getLanguage.isKindOf(Scala3Language.INSTANCE)//detect scala3 and scala3 worksheets
    if (!isScala3File)
      return text

    if (!ScalaApplicationSettings.getInstance.INDENT_PASTED_LINES_AT_CARET)
      return text

    val caret = editor.getCaretModel.getCurrentCaret
    val elementAtCaret = getElementAtCaretOrCommonParentForSelection(file, editor.getDocument, caret)
    if (elementAtCaret == null)
      return text
    if (isInsideStringLiteralOrComment(caret, elementAtCaret))
      return text

    val codeStyleSettings = CodeStyle.getSettings(file)
    val tabSize = codeStyleSettings.getTabSize(ScalaFileType.INSTANCE)
    val useTabCharacter = codeStyleSettings.useTabCharacter(ScalaFileType.INSTANCE)

    val caretIndentWhitespace: String =
      calcIndentationString(elementAtCaret, caret.getSelectionStart) match {
        case Some(value) => value
        case None =>
          return text
      }

    val caretPosition = getCaretPosition(elementAtCaret)
    if (caretPosition == CaretPosition.NotInTheBeginningOfNewLine)
      return text

    val targetCaretIndentSize: Int =
      getTargetCaretIndentSize(caretPosition, caretIndentWhitespace, tabSize, codeStyleSettings)

    val firstNonBlankLineIndentWhitespace = text
      .linesWithSeparators
      .dropWhile(StringUtils.isBlank)
      .nextOption()
      .getOrElse("")
      .takeWhile(c => c == ' ' || c == '\t')
    val firstNonBlankLineIndentSize = IndentUtil.calcIndent(firstNonBlankLineIndentWhitespace, tabSize)

    val targetIndentShouldBeNotSmallerThenCaretIndent =
      caretPosition.is[CaretPosition.InTheMiddleBodyIndentationBased]

    def fixIndent(line: String): String = {
      val lineIndentWhitespace = line.takeWhile(c => c == ' ' || c == '\t')
      val lineIndentSize = IndentUtil.calcIndent(lineIndentWhitespace, tabSize)
      val indentDiff = lineIndentSize - firstNonBlankLineIndentSize
      val indentDiffFixed = if (targetIndentShouldBeNotSmallerThenCaretIndent) indentDiff.max(0) else indentDiff
      val newIndentSize = targetCaretIndentSize + indentDiffFixed
      val lineWithoutOriginalIndent = line.stripPrefix(lineIndentWhitespace)
      val indentPrefix = if (useTabCharacter) "\t" * (newIndentSize / tabSize) else " " * newIndentSize
      indentPrefix + lineWithoutOriginalIndent
    }

    // align all lines with caret indentation
    val textWithFixedIndent = text
      .linesWithSeparators
      .map(fixIndent)
      .mkString("")

    // don't add redundant indentation for the first line, as caret is already located at this position
    textWithFixedIndent.stripPrefix(caretIndentWhitespace)
  }

  private def getElementAtCaretOrCommonParentForSelection(file: PsiFile, document: Document, caret: Caret): PsiElement = {
    val start = caret.getSelectionStart
    val end = caret.getSelectionEnd - 1
    val startElement = findElementAtCaret_WithFixedEOF(file, document, start)
    if (end > start) {
      val endElement = file.findElementAt(end)
      if (startElement != null && endElement != null)
        PsiTreeUtil.findCommonParent(startElement, endElement)
      else
        startElement
    }
    else startElement
  }
}

object Scala3IndentationBasedSyntaxCopyPastePreProcessor {

  private sealed trait CaretPosition
  private object CaretPosition {
    case class InTheMiddleBodyIndentationBased(block: ScOptionalBracesOwner) extends CaretPosition
    case class InTheMiddleBodyWithBraces(block: ScOptionalBracesOwner) extends CaretPosition
    case class AfterIncompleteDefinitionBody(e: PsiErrorElement) extends CaretPosition
    object TopLevelScalaFile extends CaretPosition
    object NotInTheBeginningOfNewLine extends CaretPosition
  }

  private def getCaretPosition(elementAtCaret: PsiElement): CaretPosition = {
    //Handle the case when caret is unindented after an empty template body or function body:
    //class A:\n<caret>
    //def foo =\n<caret>
    val prevElement = elementAtCaret.prevLeafNotWhitespaceComment
    prevElement match {
      case Some(e: PsiErrorElement) if isIncompleteDefinitionError(e) =>
        return CaretPosition.AfterIncompleteDefinitionBody(e)
      case _ =>
    }

    val parent = elementAtCaret.getParent
    parent match {
      case b: ScOptionalBracesOwner  =>
        if (b.isEnclosedByBraces)
          CaretPosition.InTheMiddleBodyWithBraces(b)
        else
          CaretPosition.InTheMiddleBodyIndentationBased(b)
      case _: ScalaFile =>
        CaretPosition.TopLevelScalaFile
      case _ =>
        CaretPosition.NotInTheBeginningOfNewLine
    }
  }

  private def getTargetCaretIndentSize(
    elementAtCaretPosition: CaretPosition,
    caretIndentWhitespace: String,
    tabSize: Int,
    codeStyleSettings: CodeStyleSettings
  ): Int =
    elementAtCaretPosition match {
      case CaretPosition.InTheMiddleBodyIndentationBased(block) =>
        getIndentOfFirstElementInBody(tabSize, block).getOrElse(codeStyleSettings.getIndentSize(ScalaFileType.INSTANCE))
      case CaretPosition.InTheMiddleBodyWithBraces(block) =>
        getIndentOfFirstElementInBody(tabSize, block).getOrElse(codeStyleSettings.getIndentSize(ScalaFileType.INSTANCE))
      case CaretPosition.AfterIncompleteDefinitionBody(e) =>
        val parentDefinitionIndentSize = IndentUtil.calcRegionIndent(e, 1)
        val indentSize = codeStyleSettings.getIndentSize(ScalaFileType.INSTANCE)
        parentDefinitionIndentSize + indentSize
      case _ =>
        IndentUtil.calcIndent(caretIndentWhitespace, tabSize)
    }

  private def getIndentOfFirstElementInBody(tabSize: Int, block: ScOptionalBracesOwner): Option[Int] = {
    val element = getFirstElementInBody(block)
    val indentStr = element.flatMap(el => calcIndentationString(el, el.getTextRange.getStartOffset))
    indentStr.map(IndentUtil.calcIndent(_, tabSize))
  }

  private def getFirstElementInBody(block: ScOptionalBracesOwner): Option[PsiElement] = {
    val braceOrColon = block.getEnclosingStartElement
    //get some element between `{` and `}` or
    braceOrColon
      .map(_.getNextSiblingNotWhitespaceComment)
      .filterNot(el => TokenSets.RBRACE_OR_END_STMT.contains(el.elementType))
  }

  /**
   * @return true for any of these {{{
   *    def foo = CARET //for def/val/var
   *`   extension (x: String) CARET
   *`   class A: CARET`
   * }}}
   */
  private def isIncompleteDefinitionError(e: PsiErrorElement): Boolean = {
    val description = e.getErrorDescription
    val isIncompleteTemplateDefinition = description == ScalaBundle.message("indented.definitions.expected")
    val isIncompleteExtension = description == ScalaBundle.message("expected.at.least.one.extension.method")
    val isIncompleteDefinitionWithAssign = description == ScalaBundle.message("expression.expected") && e.getParent.is[ScMember]
    Option(e.getPrevSibling).exists(_.elementType == ScalaTokenTypes.tASSIGN)
    isIncompleteTemplateDefinition ||
      isIncompleteExtension ||
      isIncompleteDefinitionWithAssign
  }

  private def isInsideStringLiteralOrComment(caret: Caret, elementAtCaret: PsiElement): Boolean = {
    val elementType = elementAtCaret.getNode.getElementType
    val elementTypeMatches = ScalaTokenTypes.STRING_LITERAL_TOKEN_SET.contains(elementType) ||
      ScalaTokenTypes.COMMENTS_TOKEN_SET.contains(elementType) ||
      ScalaDocElementTypes.AllElementAndTokenTypes.contains(elementType)
    elementTypeMatches && elementAtCaret.getTextRange.containsOffset(caret.getSelectionStart)
  }
}
