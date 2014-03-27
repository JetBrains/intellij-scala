package org.jetbrains.plugins.scala
package lang
package refactoring
package rename

import com.intellij.refactoring.rename.RenamePsiElementProcessor
import com.intellij.psi.PsiElement
import psi.api.statements.params.ScParameter
import psi.ScalaPsiUtil
import com.intellij.openapi.editor.Editor

class RenameScalaSyntheticParamProcessor extends RenamePsiElementProcessor with ScalaRenameProcessor {
  def canProcessElement(element: PsiElement): Boolean = realParamForSyntheticParam(element).isDefined

  override def substituteElementToRename(element: PsiElement, editor: Editor): PsiElement = realParamForSyntheticParam(element).orNull

  private def realParamForSyntheticParam(element: PsiElement) = element match {
    case x: ScParameter => ScalaPsiUtil.parameterForSyntheticParameter(x)
    case _ => None
  }
}