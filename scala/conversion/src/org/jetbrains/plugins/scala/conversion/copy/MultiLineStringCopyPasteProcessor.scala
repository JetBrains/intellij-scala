package org.jetbrains.plugins.scala.conversion.copy

import com.intellij.codeInsight.editorActions.CopyPastePreProcessor
import com.intellij.openapi.editor.{Document, Editor, RawText}
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.util.text.StringUtil.startsWithChar
import com.intellij.psi.{PsiElement, PsiFile}
import org.jetbrains.plugins.scala.conversion.copy.MultiLineStringCopyPasteProcessor._
import org.jetbrains.plugins.scala.extensions.childOf
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScInterpolatedStringLiteral, ScLiteral}
import org.jetbrains.plugins.scala.util.MultilineStringUtil

class MultiLineStringCopyPasteProcessor extends CopyPastePreProcessor {
  override def preprocessOnCopy(file: PsiFile,
                                startOffsets: Array[Int],
                                endOffsets: Array[Int],
                                text: String): String = (file, startOffsets, endOffsets) match {
    case (requiresMarginProcess(), Array(startOffset), Array(endOffset)) =>
      findStringParent(file.findElementAt(startOffset)) match {
        case Some(literal) if literal.getTextRange.containsRange(startOffset, endOffset) =>
          val marginChar = getMarginChar(literal)
          text.stripMargin(marginChar)
        case _ => null
      }
    case _ => null
  }

  override def preprocessOnPaste(project: Project,
                                 file: PsiFile,
                                 editor: Editor,
                                 text: String,
                                 rawText: RawText): String = {
    if (requiresMarginProcess(file)) {
      val offset = editor.getCaretModel.getOffset
      val element = file.findElementAt(offset)
      findStringParent(element) match {
        case Some(literal) =>
          if (offset >= literal.getTextOffset + 3 && MultilineStringUtil.looksLikeUsesMargins(literal)) {
            // TODO: handle case when some range is selected but caret is inside some multiline string
            val marginChar = getMarginChar(element)

            def marginIsMissing = !startsWithChar(text.trim, marginChar)
            def startsFromNewLine = StringUtil.isEmptyOrSpaces(linePrefix(editor.getDocument, offset))
            val needMargin = marginIsMissing && startsFromNewLine
            val marginPrefix = if (needMargin) marginChar else ""
            marginPrefix + text.replace("\n", "\n " + marginChar)
          } else text
        case _ => text
      }
    } else text
  }
}

object MultiLineStringCopyPasteProcessor {
  private object requiresMarginProcess {
    def apply(file: PsiFile): Boolean = {
      val settings = ScalaCodeStyleSettings.getInstance(file.getProject)
      settings.MULTILINE_STRING_PROCESS_MARGIN_ON_COPY_PASTE
    }

    def unapply(file: ScalaFile): Boolean = apply(file)
  }

  private def findStringParent(element: PsiElement): Option[ScLiteral] = element match {
    case literal: ScInterpolatedStringLiteral => Some(literal)
    case literal: ScLiteral if literal.isMultiLineString => Some(literal)
    case _ childOf (literal: ScInterpolatedStringLiteral) => Some(literal)
    case _ childOf (literal: ScLiteral) => Some(literal)
    case _ => None
  }

  private def linePrefix(document: Document, offset: Int): String = {
    val lineStartOffset = document.getLineStartOffset(document.getLineNumber(offset))
    val range = new TextRange(lineStartOffset, offset)
    document.getText(range)
  }

  private def getMarginChar(element: PsiElement): Char = MultilineStringUtil.getMarginChar(element)
}
