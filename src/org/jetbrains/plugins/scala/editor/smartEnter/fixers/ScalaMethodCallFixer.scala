package org.jetbrains.plugins.scala
package editor.smartEnter.fixers

import com.intellij.openapi.editor.Editor
import com.intellij.psi._
import com.intellij.util.text.CharArrayUtil
import org.jetbrains.plugins.scala.editor.smartEnter.ScalaSmartEnterProcessor
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScExpression, ScMethodCall}

/**
 * @author Ksenia.Sautina
 * @since 1/31/13
 */


class ScalaMethodCallFixer extends ScalaFixer {
  def apply(editor: Editor, processor: ScalaSmartEnterProcessor, psiElement: PsiElement) {
    val args = psiElement match {
      case call: ScMethodCall =>
        call.args
      case _ => null
    }

    if (args == null) return
    val parenthesis: PsiElement = args.lastChild.getOrElse(null)
    if (parenthesis == null || !(")" == parenthesis.getText)) {
      var endOffset: Int = -1
      var child: PsiElement = args.firstChild.getOrElse(null)
      var flag = true
      //todo tail recursion
      while (child != null && flag) {
        child match {
          case errorElement: PsiErrorElement =>
            if (errorElement.getErrorDescription.indexOf("')'") >= 0) {
              endOffset = errorElement.getTextRange.getStartOffset
              flag = false
            }
          case _ =>
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

