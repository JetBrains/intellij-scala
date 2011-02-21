package org.jetbrains.plugins.scala
package lang
package refactoring


import com.intellij.lang.refactoring.RefactoringSupportProvider
import com.intellij.psi.PsiElement
import com.intellij.refactoring.RefactoringActionHandler
import extractMethod.ScalaExtractMethodHandler
import introduceParameter.ScalaIntroduceParameterHandler
import introduceVariable.ScalaIntroduceVariableHandler
import psi.api.toplevel.typedef.ScTypeDefinition
import rename.ScalaInplaceVariableRenamer

/**
 * User: Alexander Podkhalyuzin
 * Date: 29.03.2009
 */

class ScalaRefactoringSupportProvider extends RefactoringSupportProvider {
  override def isInplaceRenameAvailable(element: PsiElement, context: PsiElement) = {
    ScalaInplaceVariableRenamer.myRenameInPlace(element, context)
  }

  override def getIntroduceConstantHandler: RefactoringActionHandler = null

  override def getIntroduceVariableHandler: RefactoringActionHandler = new ScalaIntroduceVariableHandler

  override def getIntroduceFieldHandler: RefactoringActionHandler = null

  override def getIntroduceParameterHandler: RefactoringActionHandler = new ScalaIntroduceParameterHandler

  override def isSafeDeleteAvailable(element: PsiElement): Boolean = element.isInstanceOf[ScTypeDefinition]

  override def getExtractMethodHandler: RefactoringActionHandler = new ScalaExtractMethodHandler
}