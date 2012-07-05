package org.jetbrains.plugins.scala.actions

import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiNamedElement

/**
 * @author Ksenia.Sautina
 * @since 6/22/12
 */

class Parameters(newExpression: PsiNamedElement, oldExpression: ScExpression, editor: Editor,
                          firstPart: Seq[PsiNamedElement], secondPart: Seq[PsiNamedElement]) {
  def getOldExpression = oldExpression
  def getNewExpression = newExpression
  def getEditor = editor
  def getFirstPart = firstPart
  def getSecondPart = secondPart
}
