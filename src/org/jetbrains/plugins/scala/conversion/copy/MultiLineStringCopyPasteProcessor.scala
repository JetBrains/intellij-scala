package org.jetbrains.plugins.scala.conversion.copy

import com.intellij.codeInsight.editorActions.CopyPastePreProcessor
import com.intellij.openapi.editor.{Editor, RawText}
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.{PsiElement, PsiFile}
import com.intellij.psi.tree.TokenSet
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.util.MultilineStringUtil

/**
 * User: Dmitry Naydanov
 * Date: 5/5/12
 */

class MultiLineStringCopyPasteProcessor extends CopyPastePreProcessor {
  def preprocessOnCopy(file: PsiFile, startOffsets: Array[Int], endOffsets: Array[Int], text: String): String = {
    val settings = ScalaCodeStyleSettings.getInstance(file.getProject)
    if (!file.isInstanceOf[ScalaFile] || !settings.PROCESS_MARGIN_ON_COPY_PASTE || startOffsets.length != 1 || endOffsets.length != 1) return null
    val element = file.findElementAt(startOffsets(0))

    if (checkElement(element) ||
      element.getTextRange.getStartOffset > startOffsets(0) || element.getTextRange.getEndOffset < endOffsets(0)) return null

    text stripMargin getMarginChar(element)
  }

  def preprocessOnPaste(project: Project, file: PsiFile, editor: Editor, text: String, rawText: RawText): String = {
    val settings = ScalaCodeStyleSettings.getInstance(file.getProject)
    if (!file.isInstanceOf[ScalaFile] || !settings.PROCESS_MARGIN_ON_COPY_PASTE) return text

    val offset = editor.getCaretModel.getOffset
    val document = editor.getDocument
    val element = file.findElementAt(offset)

    if (checkElement(element) ||
      offset < element.getTextOffset + 3) return text

    val marginChar = getMarginChar(element)
    val textRange = new TextRange(document.getLineStartOffset(document.getLineNumber(offset)), offset)

    (if (document.getText(textRange).trim.length == 0) marginChar else "") + text.replace("\n", "\n " + marginChar)
  }

  private def getMarginChar(element: PsiElement): Char = MultilineStringUtil.getMarginChar(element)

  private def checkElement(element: PsiElement): Boolean =
    element == null || !MultiLineStringCopyPasteProcessor.SAFE_ELEMENTS.contains(element.getNode.getElementType)
}

object MultiLineStringCopyPasteProcessor {
  private val SAFE_ELEMENTS =
    TokenSet.create(ScalaTokenTypes.tMULTILINE_STRING, ScalaTokenTypes.tINTERPOLATED_MULTILINE_STRING, ScalaTokenTypes.tINTERPOLATED_STRING_ID)
}
