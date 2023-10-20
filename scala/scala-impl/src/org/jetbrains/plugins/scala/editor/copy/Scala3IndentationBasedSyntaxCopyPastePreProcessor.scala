package org.jetbrains.plugins.scala.editor.copy

import com.intellij.application.options.CodeStyle
import com.intellij.codeInsight.editorActions.CopyPastePreProcessor
import com.intellij.openapi.editor.{Caret, Editor, RawText}
import com.intellij.openapi.project.Project
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.{PsiComment, PsiElement, PsiErrorElement, PsiFile}
import org.jetbrains.plugins.scala.editor.Scala3IndentationBasedSyntaxUtils.indentWhitespace
import org.jetbrains.plugins.scala.editor.ScalaEditorUtils.findElementAtCaret_WithFixedEOF
import org.jetbrains.plugins.scala.extensions.{ObjectExt, PsiElementExt}
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.base.ScOptionalBracesOwner
import org.jetbrains.plugins.scala.settings.ScalaApplicationSettings
import org.jetbrains.plugins.scala.util.IndentUtil
import org.jetbrains.plugins.scala.{ScalaBundle, ScalaFileType}

class Scala3IndentationBasedSyntaxCopyPastePreProcessor extends CopyPastePreProcessor {
  override def preprocessOnCopy(file: PsiFile, startOffsets: Array[Int], endOffsets: Array[Int], text: String): String = {
    if (!file.is[ScalaFile] || !ScalaApplicationSettings.getInstance.INDENT_PASTED_LINES_AT_CARET)
      return null

    if (startOffsets.length != 1 || endOffsets.length != 1)
      return null

    // only change indentation for multi-line texts
    if (!text.contains('\n'))
      return null

    // get first non-whitespace element in selection
    var firstElement = file.findElementAt(startOffsets(0)).toOption
    if (firstElement.exists(el => el.isWhitespace || el.is[PsiComment]))
      firstElement = firstElement.get.nextVisibleLeaf(true)

    if (firstElement.isEmpty || endOffsets(0) <= firstElement.get.startOffset)
    // selection contains only whitespace or comments
      return null

    // add complete first-line indent to text
    val leadingSpaceOnLine = indentWhitespace(firstElement.get)
    leadingSpaceOnLine + text.dropWhile(c => c == ' ' || c == '\t')
  }

  // the formatter is always run on pasted snippets, so we just need to adjust indentation so that the formatter recognizes it
  // this only called on single caret, paste for multiple carets is handled as raw text
  override def preprocessOnPaste(project: Project, file: PsiFile, editor: Editor, text: String, rawText: RawText): String = {
    if (!file.is[ScalaFile] || !ScalaApplicationSettings.getInstance.INDENT_PASTED_LINES_AT_CARET)
      return text

    val codeStyleSettings = CodeStyle.getSettings(file)
    val tabSize = codeStyleSettings.getTabSize(ScalaFileType.INSTANCE)
    val useTabCharacter = codeStyleSettings.useTabCharacter(ScalaFileType.INSTANCE)

    val caret = editor.getCaretModel.getCurrentCaret
    val elementAtCaret = findElementAtCaret_WithFixedEOF(file, editor.getDocument, caret.getSelectionStart)

    val caretIndentWhitespace: String =
      indentWhitespace(elementAtCaret, caret.getSelectionStart, ignoreComments = true, ignoreElementsOnLine = true)

    val caretIndentSize = getTargetCaretIndentSize(elementAtCaret, caretIndentWhitespace, caret, tabSize, codeStyleSettings)

    val firstLineIndentWhitespace = text.takeWhile(c => c == ' ' || c == '\t')
    val firstLineIndentSize = IndentUtil.calcIndent(firstLineIndentWhitespace, tabSize)

    def fixIndent(line: String): String = {
      val lineIndentWhitespace = line.takeWhile(c => c == ' ' || c == '\t')
      val lineIndentSize = IndentUtil.calcIndent(lineIndentWhitespace, tabSize)
      val newIndentSize = lineIndentSize - firstLineIndentSize + caretIndentSize
      val str = line.stripPrefix(lineIndentWhitespace)
      val prefix = if (useTabCharacter) "\t" * (newIndentSize / tabSize) else " " * newIndentSize
      prefix + str
    }

    // align all lines with caret indentation
    val textWithFixedIndent = text
      .linesWithSeparators
      .map(fixIndent)
      .mkString("")

    // don't indent first line, as caret is already indented
    textWithFixedIndent.stripPrefix(caretIndentWhitespace)
  }

  private def getTargetCaretIndentSize(
    elementAtCaret: PsiElement,
    caretIndentWhitespace: String,
    caret: Caret,
    tabSize: Int,
    codeStyleSettings: CodeStyleSettings
  ): Int = {
    if (elementAtCaret != null) {
      //Handle the case when caret is unindented after an empty template body:
      //class A:
      //<caret>
      val prevElement = elementAtCaret.prevLeafNotWhitespaceComment
      prevElement.exists {
        case e: PsiErrorElement if isIncompleteDefinitionError(e) =>
          val parentDefinitionIndentSize = IndentUtil.calcRegionIndent(e, 1)
          val indentSize = codeStyleSettings.getIndentSize(ScalaFileType.INSTANCE)
          return parentDefinitionIndentSize + indentSize
        case _ => false
      }

      //Handle the case when caret is unindented in the middle of some body (thus surrounded with properly-indented body statements)
      val parent = elementAtCaret.getParent
      parent match {
        //check if caret is in the middle of the body
        //In that case use indent of the previous declaration/statement of the body
        //TODO: test for all kinds of ScOptionalBracesOwner
        case block: ScOptionalBracesOwner if !block.isEnclosedByBraces =>
          return getIndentOfFirstElementInBody(caret, tabSize, block)
        case _ =>
      }
    }

    IndentUtil.calcIndent(caretIndentWhitespace, tabSize)
  }

  private def isIncompleteDefinitionError(e: PsiErrorElement): Boolean = {
    val description = e.getErrorDescription
    val isIncompleteTemplateDefinition = description == ScalaBundle.message("indented.definitions.expected")
    val isIncompleteExtension = description == ScalaBundle.message("expected.at.least.one.extension.method")
    val isIncompleteDefinitionWithAssign = description == ScalaBundle.message("expression.expected") &&
      Option(e.getPrevSibling).exists(_.elementType == ScalaTokenTypes.tASSIGN)
    isIncompleteTemplateDefinition ||
      isIncompleteExtension ||
      isIncompleteDefinitionWithAssign
  }

  private def getIndentOfFirstElementInBody(caret: Caret, tabSize: Int, block: ScOptionalBracesOwner): Int = {
    val element = getFirstElementInBody(block)
    val ws = indentWhitespace(element, caret.getSelectionStart, ignoreComments = true, ignoreElementsOnLine = true)
    IndentUtil.calcIndent(ws, tabSize)
  }

  private def getFirstElementInBody(block: ScOptionalBracesOwner): PsiElement = {
    val braceOrColon = block.getEnclosingStartElement
    val firstElementInBody = braceOrColon.map(_.getNextSiblingNotWhitespaceComment)
    firstElementInBody.getOrElse(block.getFirstChildNotWhitespaceComment)
  }

  override def requiresAllDocumentsToBeCommitted(editor: Editor, project: Project): Boolean = false
}
