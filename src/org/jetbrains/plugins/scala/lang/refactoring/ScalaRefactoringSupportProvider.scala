package org.jetbrains.plugins.scala
package lang
package refactoring


import com.intellij.lang.refactoring.DefaultRefactoringSupportProvider
import com.intellij.psi.PsiElement
import com.intellij.refactoring.RefactoringActionHandler
import extractMethod.ScalaExtractMethodHandler
import introduceVariable.ScalaIntroduceVariableHandler
import psi.api.toplevel.typedef.ScTypeDefinition
import rename.ScalaInplaceVariableRenamer
import com.intellij.refactoring.extractMethod.ExtractMethodHandler

/**
 * User: Alexander Podkhalyuzin
 * Date: 29.03.2009
 */

class ScalaRefactoringSupportProvider extends DefaultRefactoringSupportProvider {
  override def doInplaceRenameFor(element: PsiElement, context: PsiElement): Boolean =
    ScalaInplaceVariableRenamer.myRenameInPlace(element, context)

  override def getIntroduceConstantHandler: RefactoringActionHandler = null

  override def getIntroduceVariableHandler: RefactoringActionHandler = new ScalaIntroduceVariableHandler

  override def getIntroduceFieldHandler: RefactoringActionHandler = null

  override def getIntroduceParameterHandler: RefactoringActionHandler = null

  override def isSafeDeleteAvailable(element: PsiElement): Boolean = element.isInstanceOf[ScTypeDefinition]

  override def getExtractMethodHandler: RefactoringActionHandler = new ScalaExtractMethodHandler
}