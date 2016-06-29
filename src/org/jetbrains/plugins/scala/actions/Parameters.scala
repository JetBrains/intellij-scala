package org.jetbrains.plugins.scala.actions

import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiNamedElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression

/**
 * @author Ksenia.Sautina
 * @since 6/22/12
 */

class Parameters(newExpression: PsiNamedElement, oldExpression: ScExpression, editor: Editor,
                          firstPart: Seq[PsiNamedElement], secondPart: Seq[PsiNamedElement]) {
  def getOldExpression: ScExpression = oldExpression
  def getNewExpression: PsiNamedElement = newExpression
  def getEditor: Editor = editor
  def getFirstPart: Seq[PsiNamedElement] = firstPart
  def getSecondPart: Seq[PsiNamedElement] = secondPart
}
