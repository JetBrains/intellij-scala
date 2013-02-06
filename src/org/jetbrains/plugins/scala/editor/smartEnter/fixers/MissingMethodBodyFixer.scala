package org.jetbrains.plugins.scala
package editor.smartEnter.fixers

import com.intellij.psi._
import com.intellij.openapi.editor.{Editor, Document}
import java.lang.String
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.openapi.util.text.StringUtil
import editor.smartEnter.ScalaSmartEnterProcessor
import lang.psi.api.statements.ScFunctionDefinition
import lang.psi.api.expr.{ScExpression, ScBlockExpr}

/**
 * @author Ksenia.Sautina
 * @since 1/31/13
 */

class MissingMethodBodyFixer extends Fixer {
  def apply(editor: Editor, processor: ScalaSmartEnterProcessor, psiElement: PsiElement) {
    if (!(psiElement.isInstanceOf[ScFunctionDefinition])) return
    val method: ScFunctionDefinition = psiElement.asInstanceOf[ScFunctionDefinition]
    val containingClass: PsiClass = method.getContainingClass
    if (containingClass == null || containingClass.isInterface || method.hasModifierProperty(PsiModifier.ABSTRACT)) return
    val body: ScExpression = method.body.getOrElse(null)
    val doc: Document = editor.getDocument
    //todo types
    if (body != null) {
      val bodyText: String = body.getText
      if (bodyText.startsWith("{")) {
        val statements = body.asInstanceOf[ScBlockExpr].exprs
        if (statements.length > 0) {
          if (statements(0).isInstanceOf[PsiDeclarationStatement]) {
            if (PsiTreeUtil.getDeepestLast(statements(0)).isInstanceOf[PsiErrorElement]) {
              if (containingClass.getRBrace == null) {
                doc.insertString(body.getTextRange.getStartOffset + 1, "\n}")
              }
            }
          }
        }
      }
      return
    }
    var endOffset: Int = method.getTextRange.getEndOffset
    if (StringUtil.endsWithChar(method.getText, ';')) {
      doc.deleteString(endOffset - 1, endOffset)
      endOffset -= 1
    }
    doc.insertString(endOffset, "{\n}")
  }
}

