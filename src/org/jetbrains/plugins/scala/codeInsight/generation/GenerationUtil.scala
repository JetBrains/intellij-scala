package org.jetbrains.plugins.scala
package codeInsight.generation

import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiFile
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScTrait, ScClass, ScTemplateDefinition}
import com.intellij.psi.util.PsiTreeUtil

/**
 * Nikolay.Tropin
 * 8/19/13
 */
object GenerationUtil {
  def getClassAtCaret(editor: Editor, file: PsiFile): ScTemplateDefinition = {
    val elem = file.findElementAt(editor.getCaretModel.getOffset - 1)
    PsiTreeUtil.getParentOfType(elem, classOf[ScClass], classOf[ScTrait])
  }
}
