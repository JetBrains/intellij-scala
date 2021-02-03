package org.jetbrains.plugins.scala
package codeInspection
package collections

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement

import scala.collection.immutable.ArraySeq

/**
 * @author Nikolay.Tropin
 */
class SideEffectsInMonadicTransformationInspection extends OperationOnCollectionInspection {

  override def actionFor(implicit holder: ProblemsHolder, isOnTheFly: Boolean): PartialFunction[PsiElement, Any] = {
    case qual`.monadicMethod`(arg) =>
      exprsWithSideEffects(arg).foreach(
        expr => holder.registerProblem(expr, ScalaInspectionBundle.message("side.effects.in.monadic"), highlightType)
      )
  }


  override def possibleSimplificationTypes: ArraySeq[SimplificationType] = ArraySeq.empty
}
