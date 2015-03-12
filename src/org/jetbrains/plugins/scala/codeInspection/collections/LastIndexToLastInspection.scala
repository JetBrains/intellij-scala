package org.jetbrains.plugins.scala.codeInspection.collections

import com.intellij.codeInsight.PsiEquivalenceUtil
import org.jetbrains.plugins.scala.codeInspection.InspectionBundle
import org.jetbrains.plugins.scala.extensions.ExpressionType
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
    val genSeqType = collectionTypeFromClassName("scala.collection.GenSeq", expr.getProject)
    expr match {
      case (qual @ ExpressionType(tp))`.apply`(qual2`.sizeOrLength`() `-` literal("1"))
        if PsiEquivalenceUtil.areElementsEquivalent(qual, qual2) && tp.conforms(genSeqType) =>
        Some(replace(expr).withText(invocationText(qual, "last")))
      case _ => None
    }
  }
}
