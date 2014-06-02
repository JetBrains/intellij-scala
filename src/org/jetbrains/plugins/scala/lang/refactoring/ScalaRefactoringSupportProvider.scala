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
import psi.api.base.patterns.ScReferencePattern
import psi.api.statements._
import psi.api.base.ScFieldId
import org.jetbrains.plugins.scala.lang.refactoring.introduceField.ScalaIntroduceFieldFromExpressionHandler
import org.jetbrains.plugins.scala.lang.refactoring.extractTrait.ScalaExtractTraitHandler

/**
 * User: Alexander Podkhalyuzin
 * Date: 29.03.2009
 */

class ScalaRefactoringSupportProvider extends RefactoringSupportProvider {
  override def isInplaceRenameAvailable(element: PsiElement, context: PsiElement) = {
    false // handled by ScalaInplaceRenameHandler
  }

  override def getIntroduceConstantHandler: RefactoringActionHandler = null

  override def getIntroduceVariableHandler: RefactoringActionHandler = new ScalaIntroduceVariableHandler

  override def getIntroduceFieldHandler: RefactoringActionHandler = new ScalaIntroduceFieldFromExpressionHandler

  override def getIntroduceParameterHandler: RefactoringActionHandler = new ScalaIntroduceParameterHandler

  override def isSafeDeleteAvailable(element: PsiElement): Boolean = element match {
    case _: ScTypeDefinition | _: ScFunction | _: ScFieldId | _: ScReferencePattern => true
    case _ => false
  }

  override def getExtractMethodHandler: RefactoringActionHandler = new ScalaExtractMethodHandler
}
