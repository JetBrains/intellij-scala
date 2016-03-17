package org.jetbrains.plugins.scala.codeInspection.collections

import org.jetbrains.plugins.scala.codeInspection.InspectionBundle
import org.jetbrains.plugins.scala.extensions.ExpressionType
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.types.api.TypeSystem
import org.jetbrains.plugins.scala.lang.psi.types.{ScParameterizedType, ScType, ScTypeExt}

/**
 * @author Nikolay.Tropin
 */
class ToSetAndBackInspection extends OperationOnCollectionInspection {
  override def possibleSimplificationTypes: Array[SimplificationType] = Array(ToSetAndBackToDistinct)
}

object ToSetAndBackToDistinct extends SimplificationType {
  override def hint: String = InspectionBundle.message("replace.toSet.and.back.with.distinct")

  private val `.toSet` = invocation("toSet").from(likeCollectionClasses)

  override def getSimplification(expr: ScExpression): Option[Simplification] = {
    import expr.typeSystem
    expr match {
      case (qual @ ExpressionType(qualType))`.toSet`()`.toCollection`()
        if sameCollectionType(qualType, expr.getType().getOrAny) && (isSeq(qual) || isArray(qual)) =>
        Some(replace(expr).withText(invocationText(qual, "distinct")).highlightFrom(qual))
      case _ => None
    }
  }

  def sameCollectionType(tp1: ScType, tp2: ScType)
                        (implicit typeSystem: TypeSystem) = {
    (tp1, tp2) match {
      case (ScParameterizedType(des1, _), ScParameterizedType(des2, _)) if des1.equiv(des2) => true
      case _ => false
    }
  }
}
