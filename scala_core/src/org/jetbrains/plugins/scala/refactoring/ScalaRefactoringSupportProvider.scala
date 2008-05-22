package org.jetbrains.plugins.scala.refactoring

/**
 * @authos ilyas
*/

import com.intellij.lang.refactoring.DefaultRefactoringSupportProvider
import com.intellij.lang.refactoring.InlineHandler
import com.intellij.psi.PsiElement
import com.intellij.refactoring.RefactoringActionHandler
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition


class ScalaRefactoringSupportProvider extends DefaultRefactoringSupportProvider {

  override def isSafeDeleteAvailable(element: PsiElement) =  element match {
    case _ : ScTypeDefinition=> true
    case _ => false
  }

}