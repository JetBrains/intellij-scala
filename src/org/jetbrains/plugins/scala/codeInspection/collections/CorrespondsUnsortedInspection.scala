package org.jetbrains.plugins.scala.codeInspection.collections

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.codeInspection.InspectionBundle
import org.jetbrains.plugins.scala.extensions.Both
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression

/**
 * @author Nikolay.Tropin
 */
class CorrespondsUnsortedInspection extends OperationOnCollectionInspection {
  override def possibleSimplificationTypes: Array[SimplificationType] = Array.empty

  override def actionFor(implicit holder: ProblemsHolder): PartialFunction[PsiElement, Any] = {
    case Both(expr: ScExpression, (left`.sameElements`(right))) if isUnsorted(left) || isUnsorted(right) =>
      holder.registerProblem(refNameId(expr).getOrElse(expr), InspectionBundle.message("sameElements.unsorted"), highlightType)
    case Both(expr: ScExpression, (left`.corresponds`(right, _))) if isIterator(left) && isUnsorted(right) =>
    //corresponds signature imply that check is needed for iterators only
      holder.registerProblem(refNameId(expr).getOrElse(expr), InspectionBundle.message("corresponds.unsorted"), highlightType)
  }

  private def isUnsorted(expr: ScExpression): Boolean =
    !(isSeq(expr) || isSortedMap(expr) || isSortedSet(expr) || isArray(expr) || isIterator(expr))
}
