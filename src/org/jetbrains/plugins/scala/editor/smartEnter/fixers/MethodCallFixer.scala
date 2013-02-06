package org.jetbrains.plugins.scala
package editor.smartEnter.fixers

import com.intellij.psi._
import com.intellij.util.text.CharArrayUtil
import com.intellij.openapi.editor.Editor
import editor.smartEnter.ScalaSmartEnterProcessor
import lang.psi.api.expr.{ScExpression, ScMethodCall}

/**
 * @author Ksenia.Sautina
 * @since 1/31/13
 */


class MethodCallFixer extends Fixer {
  def apply(editor: Editor, processor: ScalaSmartEnterProcessor, psiElement: PsiElement) {
    val args = if (psiElement.isInstanceOf[ScMethodCall]) {
      (psiElement.asInstanceOf[ScMethodCall]).args
    }  else null

    if (args == null) return
    val parenth: PsiElement = args.lastChild.getOrElse(null)
    if (parenth == null || !(")" == parenth.getText)) {
      var endOffset: Int = -1
      var child: PsiElement = args.firstChild.getOrElse(null)
      var flag = true
      while (child != null && flag) {
        if (child.isInstanceOf[PsiErrorElement]) {
          val errorElement: PsiErrorElement = child.asInstanceOf[PsiErrorElement]
          if (errorElement.getErrorDescription.indexOf("')'") >= 0) {
            endOffset = errorElement.getTextRange.getStartOffset
            flag = false
          }
        }
        child = child.getNextSibling
      }
      if (endOffset == -1) {
        endOffset = args.getTextRange.getEndOffset
      }
      val params: Seq[ScExpression] = args.exprs
      if (params.length > 0 && startLine(editor, args) != startLine(editor, params(0))) {
        endOffset = args.getTextRange.getStartOffset + 1
      }
      endOffset = CharArrayUtil.shiftBackward(editor.getDocument.getCharsSequence, endOffset - 1, " \t\n") + 1
      editor.getDocument.insertString(endOffset, ")")
    }
  }

  private def startLine(editor: Editor, psiElement: PsiElement): Int = {
    editor.getDocument.getLineNumber(psiElement.getTextRange.getStartOffset)
  }
}

