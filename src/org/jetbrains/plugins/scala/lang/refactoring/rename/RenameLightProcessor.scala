package org.jetbrains.plugins.scala.lang.refactoring.rename

import org.jetbrains.plugins.scala.lang.psi.fake.FakePsiMethod
import com.intellij.refactoring.rename.RenamePsiElementProcessor
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.light.{StaticPsiTypedDefinitionWrapper, StaticPsiMethodWrapper, PsiTypedDefinitionWrapper, ScFunctionWrapper}

/**
 * User: Alexander Podkhalyuzin
 * Date: 08.09.2009
 */
class RenameLightProcessor extends RenamePsiElementProcessor {
  def canProcessElement(element: PsiElement): Boolean = {
    element match {
      case f: FakePsiMethod => true
      case f: ScFunctionWrapper => true
      case d: PsiTypedDefinitionWrapper => true
      case d: StaticPsiTypedDefinitionWrapper => true
      case p: StaticPsiMethodWrapper => true
      case _ => false
    }
  }

  override def substituteElementToRename(element: PsiElement, editor: Editor): PsiElement = {
    element match {
      case f: FakePsiMethod => null
      case f: ScFunctionWrapper => f.function
      case d: PsiTypedDefinitionWrapper => d.typedDefinition
      case d: StaticPsiTypedDefinitionWrapper => d.typedDefinition
      case p: StaticPsiMethodWrapper => p.method
      case _ => element
    }
  }
}