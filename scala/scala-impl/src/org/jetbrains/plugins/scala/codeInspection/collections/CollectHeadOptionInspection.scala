package org.jetbrains.plugins.scala.codeInspection.collections

import org.jetbrains.plugins.scala.codeInspection.ScalaInspectionBundle
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression

import scala.collection.immutable.ArraySeq

class CollectHeadOptionInspection extends OperationOnCollectionInspection {
  override def possibleSimplificationTypes: ArraySeq[SimplificationType] = ArraySeq(CollectHeadOption)
}

object CollectHeadOption extends SimplificationType {
  override def hint: String = ScalaInspectionBundle.message("replace.collect.headOption.with.collectFirst")

  override def getSimplification(expr: ScExpression): Option[Simplification] = {
    expr match {
      // TODO infix notation?
      case `.headOption`(qual `.collect` (f)) =>
        Some(replace(expr).withText(invocationText(qual, "collectFirst", f)).highlightFrom(qual))
      case _ => None
    }
  }

}
