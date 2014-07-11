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

    forStatement.body match {
      case None =>
        val (eltToInsertAfter, text) = forStatement.getRightParenthesis match {
          case None => (forStatement, "){\n\n}")
          case Some(parenth) =>  (parenth, "{\n\n}")
        }
        doc.insertString(eltToInsertAfter.getTextRange.getEndOffset, text)
      case Some(_) =>
    }
  }
}

