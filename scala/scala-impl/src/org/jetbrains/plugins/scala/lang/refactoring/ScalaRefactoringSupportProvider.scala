package org.jetbrains.plugins.scala
package lang
package refactoring


import com.intellij.lang.refactoring.RefactoringSupportProvider
import com.intellij.psi.PsiElement
import com.intellij.refactoring.RefactoringActionHandler
import com.intellij.refactoring.changeSignature.ChangeSignatureHandler
import org.jetbrains.plugins.scala.lang.psi.api.base.ScFieldId
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScReferencePattern
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScParameter, ScTypeParam}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import org.jetbrains.plugins.scala.lang.refactoring.changeSignature.ScalaChangeSignatureHandler
import org.jetbrains.plugins.scala.lang.refactoring.extractMethod.ScalaExtractMethodHandler
import org.jetbrains.plugins.scala.lang.refactoring.introduceField.ScalaIntroduceFieldFromExpressionHandler
import org.jetbrains.plugins.scala.lang.refactoring.introduceParameter.ScalaIntroduceParameterHandler
import org.jetbrains.plugins.scala.lang.refactoring.introduceVariable.ScalaIntroduceVariableHandler

class ScalaRefactoringSupportProvider extends RefactoringSupportProvider {
  override def isInplaceRenameAvailable(element: PsiElement, context: PsiElement): Boolean = {
    false // handled by ScalaInplaceRenameHandler
  }

  override def getIntroduceConstantHandler: RefactoringActionHandler = null

  override def getIntroduceVariableHandler: RefactoringActionHandler = new ScalaIntroduceVariableHandler

  override def getIntroduceFieldHandler: RefactoringActionHandler = new ScalaIntroduceFieldFromExpressionHandler

  override def getIntroduceParameterHandler: RefactoringActionHandler = new ScalaIntroduceParameterHandler

  override def isSafeDeleteAvailable(element: PsiElement): Boolean = element match {
    case _: ScTypeDefinition | _: ScFunction | _: ScFieldId | _: ScReferencePattern | _: ScParameter | _: ScTypeParam => true
    case _ => false
  }

  override def getExtractMethodHandler: RefactoringActionHandler = new ScalaExtractMethodHandler

  override def getChangeSignatureHandler: ChangeSignatureHandler = new ScalaChangeSignatureHandler
}
