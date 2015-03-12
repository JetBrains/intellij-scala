package org.jetbrains.plugins.scala.codeInspection.collections

import com.intellij.codeInsight.PsiEquivalenceUtil
import org.jetbrains.plugins.scala.codeInspection.InspectionBundle
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression

/**
 * @author Nikolay.Tropin
 */
class LastIndexToLastInspection extends OperationOnCollectionInspection {
  override def possibleSimplificationTypes: Array[SimplificationType] = Array(LastIndexToLast)
}

object LastIndexToLast extends SimplificationType {
  override def hint: String = InspectionBundle.message("replace.with.last")

  override def getSimplification(expr: ScExpression): Option[Simplification] = {
    expr match {
      case qual`.apply`(qual2`.sizeOrLength`() `-` literal("1"))
        if PsiEquivalenceUtil.areElementsEquivalent(qual, qual2) && isSeq(qual) =>
        Some(replace(expr).withText(invocationText(qual, "last")))
      case _ => None
    }
  }
}
