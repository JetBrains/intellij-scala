package org.jetbrains.plugins.scala
package editor.smartEnter.fixers

import com.intellij.psi.PsiElement
import com.intellij.codeInsight.editorActions.enter.EnterAfterUnmatchedBraceHandler
import com.intellij.openapi.editor.Editor
import editor.smartEnter.ScalaSmartEnterProcessor
import com.intellij.psi.util.PsiTreeUtil
import lang.psi.api.expr.{ScExpression, ScBlock}
import com.intellij.openapi.fileTypes.FileType

/**
 * @author Ksenia.Sautina
 * @since 1/30/13
 */

class BlockBraceFixer extends Fixer {
  def apply(editor: Editor, processor: ScalaSmartEnterProcessor, psiElement: PsiElement) {
    val block = PsiTreeUtil.getParentOfType(psiElement, classOf[ScBlock], false)
    if (block != null && afterUnmatchedBrace(editor, psiElement.getContainingFile.getFileType)) {
      var stopOffset: Int = block.getTextRange.getEndOffset
      val statements: Seq[ScExpression] = block.exprs
      if (statements.length > 0) {
        stopOffset = statements(0).getTextRange.getEndOffset
      }
      editor.getDocument.insertString(stopOffset, "}")
    }
  }

  private def afterUnmatchedBrace(editor: Editor, fileType: FileType): Boolean = {
    EnterAfterUnmatchedBraceHandler.isAfterUnmatchedLBrace(editor, editor.getCaretModel.getOffset, fileType)
  }
}