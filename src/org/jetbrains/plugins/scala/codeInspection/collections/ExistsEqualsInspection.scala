package org.jetbrains.plugins.scala
package codeInspection.collections

import org.jetbrains.plugins.scala.codeInspection.InspectionBundle
import org.jetbrains.plugins.scala.codeInspection.collections.OperationOnCollectionsUtil._
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScMethodCall, ScExpression, ScReferenceExpression}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory

/**
 * Nikolay.Tropin
 * 2014-05-06
 */
class ExistsEqualsInspection extends OperationOnCollectionInspection {
  override def possibleSimplificationTypes: Array[SimplificationType] =
    Array(new ExistsEquals(this))
}

class ExistsEquals(inspection: OperationOnCollectionInspection) extends SimplificationType(inspection) {
  override def getSimplification(single: MethodRepr) = {
    single.itself match {
      case MethodRepr(_, Some(qual), Some(ref), Seq(arg))
        if ref.refName == "exists" &&
                checkResolve(ref, likeCollectionClasses) =>

        isEqualsWithSomeExpr(arg).toList.flatMap { expr =>
          if (canBeReplacedWithContains(qual, expr)) createSimplification(single, single.itself, "contains", Seq(expr))
          else Nil
        }
      case _ => Nil
    }
  }

  override def hint = InspectionBundle.message("exists.equals.hint")

  private def canBeReplacedWithContains(qual: ScExpression, arg: ScExpression) = {
    val exprText = s"${qual.getText}.contains(${arg.getText})"
    ScalaPsiElementFactory.createExpressionWithContextFromText(exprText, qual.getContext, qual) match {
      case ScMethodCall(ref: ScReferenceExpression, Seq(a)) =>
        ref.resolve() != null &&
        a.expectedType(fromUnderscore = false).exists(a.getType().getOrNothing.conforms(_))
      case _ => false
    }

  }
}
