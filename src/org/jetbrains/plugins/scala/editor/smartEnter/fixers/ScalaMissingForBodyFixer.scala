package org.jetbrains.plugins.scala
package editor.smartEnter.fixers

import com.intellij.psi._
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.openapi.editor.{Editor, Document}
import java.lang.String
import editor.smartEnter.ScalaSmartEnterProcessor
import lang.psi.api.expr.{ScBlockExpr, ScForStatement}

/**
 * @author Ksenia.Sautina
 * @since 2/5/13
 */
class ScalaMissingForBodyFixer  extends ScalaFixer {
  def apply(editor: Editor, processor: ScalaSmartEnterProcessor, psiElement: PsiElement) {
    val forStatement = PsiTreeUtil.getParentOfType(psiElement, classOf[ScForStatement], false)
    if (forStatement == null) return
    val doc: Document = editor.getDocument
    val body: PsiElement = forStatement.body.getOrElse(null)
    if (body.isInstanceOf[ScBlockExpr]) return
    if (body != null && startLine(doc, body) == startLine(doc, forStatement)) return
    var eltToInsertAfter: PsiElement = forStatement.getRightParenthesis.getOrElse(null)
    var text: String = "{\n\n}"
    if (eltToInsertAfter == null) {
      eltToInsertAfter = forStatement
      text = "){\n\n}"
    }
    doc.insertString(eltToInsertAfter.getTextRange.getEndOffset, text)
  }

  private def startLine(doc: Document, psiElement: PsiElement): Int = {
    doc.getLineNumber(psiElement.getTextRange.getStartOffset)
  }
}

