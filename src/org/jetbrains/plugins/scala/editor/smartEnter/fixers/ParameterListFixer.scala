package org.jetbrains.plugins.scala
package editor.smartEnter.fixers

import com.intellij.psi.PsiElement
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.editor.Editor
import editor.smartEnter.ScalaSmartEnterProcessor
import com.intellij.psi.util.PsiTreeUtil
import lang.psi.api.statements.params.ScParameterClause

/**
 * @author Ksenia.Sautina
 * @since 1/31/13
 */

class ParameterListFixer extends Fixer {
  def apply(editor: Editor, processor: ScalaSmartEnterProcessor, psiElement: PsiElement) {
    //todo type
    val list = PsiTreeUtil.getParentOfType(psiElement, classOf[ScParameterClause], false)
    if (list != null) {
      if (!StringUtil.endsWithChar(psiElement.getText, ')')) {
        var offset: Int = 0
        val params = list.parameters.toArray
        if (params == null || params.length == 0) {
          offset = list.getTextRange.getStartOffset + 1
        }
        else {
          offset = params(params.length - 1).getTextRange.getEndOffset
        }
        editor.getDocument.insertString(offset, ")")
      }
    }
  }
}

