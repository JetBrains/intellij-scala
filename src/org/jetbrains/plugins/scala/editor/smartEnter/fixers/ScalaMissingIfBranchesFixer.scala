package org.jetbrains.plugins.scala
package editor.smartEnter.fixers

import com.intellij.openapi.editor.{Document, Editor}
import com.intellij.psi._
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.editor.smartEnter.ScalaSmartEnterProcessor
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScBlockExpr, ScExpression, ScIfStmt}

/**
 * @author Ksenia.Sautina
 * @since 1/31/13
 */

class ScalaMissingIfBranchesFixer extends ScalaFixer {
  def apply(editor: Editor, processor: ScalaSmartEnterProcessor, psiElement: PsiElement) {
    val ifStatement = PsiTreeUtil.getParentOfType(psiElement, classOf[ScIfStmt], false)
    if (ifStatement != null) {
      val doc: Document = editor.getDocument
      val elseBranch: ScExpression = ifStatement.elseBranch.orNull
      val thenBranch: ScExpression = ifStatement.thenBranch.orNull
      if (thenBranch.isInstanceOf[ScBlockExpr]) return
      var transformingOneLiner: Boolean = false
      if (thenBranch != null && startLine(doc, thenBranch) == startLine(doc, ifStatement)) {
        if (ifStatement.condition.orNull != null) {
          return
        }
        transformingOneLiner = true
      }
      val rParenth = ifStatement.getRightParenthesis.orNull
      assert(rParenth != null)
      if (elseBranch == null && !transformingOneLiner || thenBranch == null) {
        doc.insertString(rParenth.getTextRange.getEndOffset, "{\n\n}")
      }
      else {
        doc.insertString(rParenth.getTextRange.getEndOffset, "{")
        doc.insertString(thenBranch.getTextRange.getEndOffset + 1, "\n\n}")
      }
    }
  }

  private def startLine(doc: Document, psiElement: PsiElement): Int = {
    doc.getLineNumber(psiElement.getTextRange.getStartOffset)
  }
}

