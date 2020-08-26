package org.jetbrains.plugins.scala
package codeInspection
package collections

import com.intellij.codeInsight.PsiEquivalenceUtil
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression

/**
 * @author Nikolay.Tropin
 */
class LastIndexToLastInspection extends OperationOnCollectionInspection {
  override def possibleSimplificationTypes: Array[SimplificationType] = Array(LastIndexToLast)
}

object LastIndexToLast extends SimplificationType {
  override def hint: String = ScalaInspectionBundle.message("replace.with.last")

  override def getSimplification(expr: ScExpression): Option[Simplification] = {
    expr match {
      // TODO infix notation?
      case qual`.apply`(`.sizeOrLength`(qual2) `-` literal("1"))
        if PsiEquivalenceUtil.areElementsEquivalent(qual, qual2)
          && isSeq(qual) && !isIndexedSeq(qual) =>
        Some(replace(expr).withText(invocationText(qual, "last")))
      case _ => None
    }
  }
}
