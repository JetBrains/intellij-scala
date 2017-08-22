package org.jetbrains.plugins.scala.codeInspection.collections

import org.jetbrains.plugins.scala.codeInspection.InspectionBundle
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression

/**
  * @author Ignat Loskutov
  */

class ExistsForallReplaceInspection extends OperationOnCollectionInspection {
  override def possibleSimplificationTypes: Array[SimplificationType] =
    Array(ReplaceForallWithExists, ReplaceExistsWithForall)
}

private object NegatedPredicate {
  def unapply(arg: ScExpression): Option[ScExpression] = arg match {
    case `!`(pred) => Some(pred)
    case _ => None
  }
}

object ReplaceForallWithExists extends SimplificationType {
  override def hint: String = InspectionBundle.message("replace.with.exists")
  override def getSimplification(expr: ScExpression): Option[Simplification] = {
    expr match {
      case `!`(qual`.forall`(NegatedPredicate(pred))) =>
        Some(replace(expr).withText(invocationText(qual, "exists", pred)).highlightAll)
      case _ => None
    }
  }
}

object ReplaceExistsWithForall extends SimplificationType {
  override def hint: String = InspectionBundle.message("replace.with.forall")
  override def getSimplification(expr: ScExpression): Option[Simplification] = {
    expr match {
      case `!`(qual`.exists`(NegatedPredicate(pred))) =>
        Some(replace(expr).withText(invocationText(qual, "forall", pred)).highlightAll)
      case _ => None
    }
  }
}
