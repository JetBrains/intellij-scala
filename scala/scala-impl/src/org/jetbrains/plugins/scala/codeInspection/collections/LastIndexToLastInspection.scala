package org.jetbrains.plugins.scala.codeInspection.collections

import com.intellij.codeInsight.PsiEquivalenceUtil
import org.jetbrains.plugins.scala.codeInspection.ScalaInspectionBundle
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression

import scala.collection.immutable.ArraySeq

class LastIndexToLastInspection extends OperationOnCollectionInspection {
  override def possibleSimplificationTypes: ArraySeq[SimplificationType] = ArraySeq(LastIndexToLast)
}

object LastIndexToLast extends SimplificationType {
  override def hint: String = ScalaInspectionBundle.message("replace.with.last")

  override def getSimplification(expr: ScExpression): Option[Simplification] = {
    expr match {
      // TODO infix notation?
      case qual`.apply`(`.sizeOrLength`(qual2) `-` literal("1"))
        if areElementsEquivalent(qual, qual2)
          && isSeq(qual) && !isIndexedSeq(qual) =>
        Some(replace(expr).withText(invocationText(qual, "last")).highlightFrom(qual))
      case _ => None
    }
  }
}
