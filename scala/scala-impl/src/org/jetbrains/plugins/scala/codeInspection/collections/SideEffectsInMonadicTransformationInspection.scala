package org.jetbrains.plugins.scala.codeInspection.collections

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.codeInspection.InspectionBundle

/**
 * @author Nikolay.Tropin
 */
class SideEffectsInMonadicTransformationInspection extends OperationOnCollectionInspection {

  override def actionFor(implicit holder: ProblemsHolder): PartialFunction[PsiElement, Any] = {
    case qual`.monadicMethod`(arg) =>
      exprsWithSideEffects(arg).foreach {
        case expr => holder.registerProblem(expr, InspectionBundle.message("side.effects.in.monadic"), highlightType)
      }
  }


  override def possibleSimplificationTypes: Array[SimplificationType] = Array.empty
}
