package org.jetbrains.plugins.scala.codeInspection.collections

import com.intellij.codeInspection.ProblemHighlightType
import org.jetbrains.plugins.scala.codeInspection.InspectionBundle
import org.jetbrains.plugins.scala.extensions.{ChildOf, ExpressionType}
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScExpression, ScGenericCall}
import org.jetbrains.plugins.scala.lang.psi.types.ScTypeExt
import org.jetbrains.plugins.scala.project.ProjectExt

/**
 * @author Nikolay.Tropin
 */

object RedundantCollectionConversion extends SimplificationType {
  override def hint: String = InspectionBundle.message("redundant.collection.conversion")

  override def getSimplification(expr: ScExpression) = {
    implicit val typeSystem = expr.getProject.typeSystem
    val withGeneric = expr match {
      case ChildOf(gc: ScGenericCall) => gc
      case ref => ref
    }
    val typeAfterConversion = withGeneric.getType().getOrAny
    withGeneric match {
      case (base@ExpressionType(baseType)) `.toCollection` () if baseType.conforms(typeAfterConversion) =>
        val simplification = replace(withGeneric).withText(base.getText).highlightFrom(base)
        Some(simplification)
      case _ => None
    }
  }
}

class RedundantCollectionConversionInspection extends OperationOnCollectionInspection {
  override def highlightType = ProblemHighlightType.LIKE_UNUSED_SYMBOL

  override def possibleSimplificationTypes: Array[SimplificationType] = Array(RedundantCollectionConversion)
}
