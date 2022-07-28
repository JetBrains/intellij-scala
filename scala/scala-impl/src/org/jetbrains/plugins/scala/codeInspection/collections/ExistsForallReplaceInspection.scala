package org.jetbrains.plugins.scala
package codeInspection
package collections

import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory

import scala.collection.immutable.ArraySeq

class ExistsForallReplaceInspection extends OperationOnCollectionInspection {
  override def possibleSimplificationTypes: ArraySeq[SimplificationType] =
    ArraySeq(ReplaceForallWithExists, ReplaceExistsWithForall)
}

private object NegatedPredicate {
  def unapply(arg: ScExpression): Option[ScExpression] = arg match {
    case `!`(pred) => Some(pred)
    case fExpr @ ScFunctionExpr(_, Some(`!`(pred))) =>
      val newText = s"${fExpr.params.getText} => ${pred.getText}"
      Some(ScalaPsiElementFactory.createExpressionWithContextFromText(newText, arg.getContext, arg))
    case _ => None
  }
}

object ReplaceForallWithExists extends SimplificationType {
  override def hint: String = ScalaInspectionBundle.message("replace.with.exists")
  override def getSimplification(expr: ScExpression): Option[Simplification] = {
    expr match {
      case `!`(qual`.forall`(NegatedPredicate(pred))) =>
        Some(replace(expr).withText(invocationText(qual, "exists", pred)).highlightAll)
      case _ => None
    }
  }
}

object ReplaceExistsWithForall extends SimplificationType {
  override def hint: String = ScalaInspectionBundle.message("replace.with.forall")
  override def getSimplification(expr: ScExpression): Option[Simplification] = {
    expr match {
      case `!`(qual`.exists`(NegatedPredicate(pred))) =>
        Some(replace(expr).withText(invocationText(qual, "forall", pred)).highlightAll)
      case _ => None
    }
  }
}
