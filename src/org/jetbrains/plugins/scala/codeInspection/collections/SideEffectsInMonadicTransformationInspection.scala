package org.jetbrains.plugins.scala.codeInspection.collections

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.codeInspection.InspectionBundle
import org.jetbrains.plugins.scala.codeInspection.collections.SideEffectsInMonadicTransformationInspection._

/**
 * @author Nikolay.Tropin
 */
class SideEffectsInMonadicTransformationInspection extends OperationOnCollectionInspection {

  override def actionFor(holder: ProblemsHolder): PartialFunction[PsiElement, Any] = {
    case MethodRepr(_, _, Some(ref), Seq(arg))
      if monadicMethods.contains(ref.refName) &&
              OperationOnCollectionsUtil.checkResolve(ref, getLikeCollectionClasses) =>
      OperationOnCollectionsUtil.exprsWithSideEffect(arg).foreach {
        case expr => holder.registerProblem(expr, InspectionBundle.message("side.effects.in.monadic"), highlightType)
        case _ =>
      }
  }


  override def possibleSimplificationTypes: Array[SimplificationType] = Array.empty
}

object SideEffectsInMonadicTransformationInspection {
  val monadicMethods = Set("map", "flatMap", "filter", "withFilter")
}
