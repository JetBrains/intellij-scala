package org.jetbrains.plugins.scala.codeInspection.collections

import com.intellij.codeInspection.ProblemsHolder
import org.jetbrains.plugins.scala.codeInspection.{PsiElementVisitorSimple, ScalaInspectionBundle}
import org.jetbrains.plugins.scala.extensions.&
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression

import scala.collection.immutable.ArraySeq

class CorrespondsUnsortedInspection extends OperationOnCollectionInspection {
  override def possibleSimplificationTypes: ArraySeq[SimplificationType] = ArraySeq.empty

  override def buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitorSimple = {
    case (expr: ScExpression) & (left`.sameElements`(right)) if isUnsorted(left) || isUnsorted(right) =>
      holder.registerProblem(refNameId(expr).getOrElse(expr), ScalaInspectionBundle.message("sameElements.unsorted"))
    case (expr: ScExpression) & (left`.corresponds`(right, _)) if isIterator(left) && isUnsorted(right) =>
    //corresponds signature imply that check is needed for iterators only
      holder.registerProblem(refNameId(expr).getOrElse(expr), ScalaInspectionBundle.message("corresponds.unsorted"))
    case _ =>
  }

  private def isUnsorted(expr: ScExpression): Boolean =
    !(isSeq(expr) || isSortedMap(expr) || isSortedSet(expr) || isArray(expr) || isIterator(expr))
}
