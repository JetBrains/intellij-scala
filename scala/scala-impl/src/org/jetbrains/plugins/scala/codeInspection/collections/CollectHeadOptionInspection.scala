package org.jetbrains.plugins.scala.codeInspection.collections

import org.jetbrains.plugins.scala.codeInspection.InspectionBundle
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression

/**
  * @author t-kameyama
  */
class CollectHeadOptionInspection extends OperationOnCollectionInspection {
  override def possibleSimplificationTypes: Array[SimplificationType] = Array(CollectHeadOption)
}

object CollectHeadOption extends SimplificationType {
  override def hint: String = InspectionBundle.message("replace.collect.headOption.with.collectFirst")

  override def getSimplification(expr: ScExpression): Option[Simplification] = {
    expr match {
      case qual `.collect` (f) `.headOption` () =>
        Some(replace(expr).withText(invocationText(qual, "collectFirst", f)).highlightFrom(qual))
      case _ => None
    }
  }

}
