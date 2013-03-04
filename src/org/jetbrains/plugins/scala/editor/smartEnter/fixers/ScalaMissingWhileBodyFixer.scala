package org.jetbrains.plugins.scala
package editor.smartEnter.fixers

import com.intellij.psi.{PsiBlockStatement, PsiElement}
import com.intellij.openapi.editor.{Editor, Document}
import editor.smartEnter.ScalaSmartEnterProcessor
import com.intellij.psi.util.PsiTreeUtil
import lang.psi.api.expr.{ScBlockExpr, ScWhileStmt}

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
    val body: PsiElement = whileStatement.body.getOrElse(null)
    if (body.isInstanceOf[ScBlockExpr]) return
    if (body != null && startLine(doc, body) == startLine(doc, whileStatement) && whileStatement.condition.getOrElse(null) != null) return
    val rParenth = whileStatement.getRightParenthesis.getOrElse(null)
    assert(rParenth != null)
    doc.insertString(rParenth.getTextRange.getEndOffset, "{\n\n}")
  }
}

