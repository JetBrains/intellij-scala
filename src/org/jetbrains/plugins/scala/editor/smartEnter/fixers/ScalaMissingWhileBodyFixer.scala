package org.jetbrains.plugins.scala
package editor.smartEnter.fixers

import com.intellij.openapi.editor.{Document, Editor}
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.editor.smartEnter.ScalaSmartEnterProcessor
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScBlockExpr, ScWhileStmt}

/**
 * @author Ksenia.Sautina
 * @since 2/5/13
 */

class ScalaMissingWhileBodyFixer extends ScalaFixer {
  private def startLine(doc: Document, psiElement: PsiElement): Int = {
    doc.getLineNumber(psiElement.getTextRange.getStartOffset)
  }


  def apply(editor: Editor, processor: ScalaSmartEnterProcessor, psiElement: PsiElement) {
    val whileStatement = PsiTreeUtil.getParentOfType(psiElement, classOf[ScWhileStmt], false)
    if (whileStatement == null) return
    val doc: Document = editor.getDocument
    val body: PsiElement = whileStatement.body.orNull
    if (body.isInstanceOf[ScBlockExpr]) return
    if (body != null && startLine(doc, body) == startLine(doc, whileStatement) && whileStatement.condition.orNull != null) return
    val rParenth = whileStatement.getRightParenthesis.orNull
    assert(rParenth != null)
    doc.insertString(rParenth.getTextRange.getEndOffset, "{\n\n}")
  }
}

