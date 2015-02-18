package org.jetbrains.plugins.scala
package codeInspection.collections

import org.jetbrains.plugins.scala.codeInspection.InspectionBundle
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScInfixExpr
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory

/**
 * Nikolay.Tropin
 * 2014-05-07
 */
class FilterSizeCheckInspection extends OperationOnCollectionInspection {
  override def possibleSimplificationTypes: Array[SimplificationType] =
    Array(new FilterSizeCheck(this))
}

class FilterSizeCheck(inspection: OperationOnCollectionInspection) extends SimplificationType(inspection) {
  override def hint = InspectionBundle.message("filter.size.check.hint")

  override def getSimplification(single: MethodRepr): List[Simplification] = {
    single.optionalMethodRef match {
      case Some(op) if Seq(">", ">=", "==").contains(op.refName) =>
      case _ => return Nil
    }

    single.itself match {
      case MethodRepr(_, Some(lhs), Some(oper), Seq(arg)) =>
        lhs match {
          case MethodSeq(last, second, _*) =>
            val innerSmpl = new FilterSize(inspection).getSimplification(last, second)

            if (OperationOnCollectionsUtil.exprsWithSideEffect(second.itself).nonEmpty) return Nil

            (innerSmpl, oper.refName, arg.getText) match {
              case (Nil, _, _) => Nil
              case (_, ">", "0") | (_, ">=", "1") =>
                createSimplification(second, single.itself, "exists", second.args)
              case (_, "==", "0") =>
                createSimplification(second, single.itself, "exists", second.args).map {
                  smpl =>
                    ScalaPsiElementFactory.createExpressionFromText(smpl.replacementText, lhs.getManager) match {
                      case _: ScInfixExpr => smpl.copy(replacementText = s"!(${smpl.replacementText})")
                      case _ => smpl.copy(replacementText = "!" + smpl.replacementText)
                    }
                }
              case _ => Nil
            }
          case _ => Nil
        }
      case _ => Nil
    }
  }
}