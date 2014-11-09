package org.jetbrains.plugins.scala
package lang
package refactoring
package rename

import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.refactoring.rename.RenamePsiElementProcessor
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter

class RenameScalaSyntheticParamProcessor extends RenamePsiElementProcessor with ScalaRenameProcessor {
  def canProcessElement(element: PsiElement): Boolean = realParamForSyntheticParam(element).isDefined

  override def substituteElementToRename(element: PsiElement, editor: Editor): PsiElement = realParamForSyntheticParam(element).orNull

  private def realParamForSyntheticParam(element: PsiElement) = element match {
    case x: ScParameter => ScalaPsiUtil.parameterForSyntheticParameter(x)
    case _ => None
  }
}