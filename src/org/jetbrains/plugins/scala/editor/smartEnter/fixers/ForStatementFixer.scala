package org.jetbrains.plugins.scala
package editor.smartEnter.fixers

import com.intellij.psi._
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.editor.Editor
import editor.smartEnter.ScalaSmartEnterProcessor
import util.PsiTreeUtil
import lang.psi.api.expr.ScForStatement

/**
 * @author Ksenia.Sautina
 * @since 1/29/13
 */

@SuppressWarnings(Array("HardCodedStringLiteral"))
class ForStatementFixer extends Fixer {
  def apply(editor: Editor, processor: ScalaSmartEnterProcessor, psiElement: PsiElement) {
    val forStatement = PsiTreeUtil.getParentOfType(psiElement, classOf[ScForStatement], false)
    if (forStatement != null) {
      val lParenth = forStatement.getLeftParenthesis.getOrElse(null)
      val rParenth = forStatement.getRightParenthesis.getOrElse(null)
      if (lParenth == null || rParenth == null) {
        val textRange: TextRange = forStatement.getTextRange
        editor.getDocument.replaceString(textRange.getStartOffset, textRange.getEndOffset, "for () {\n}")
        processor.registerUnresolvedError(textRange.getStartOffset + "for (".length)
        return
      }
    }
  }
}

