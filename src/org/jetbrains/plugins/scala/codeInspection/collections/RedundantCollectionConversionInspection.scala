package org.jetbrains.plugins.scala.codeInspection.collections

import com.intellij.codeInspection.ProblemHighlightType
import org.jetbrains.plugins.scala.codeInspection.InspectionBundle
import org.jetbrains.plugins.scala.codeInspection.collections.OperationOnCollectionsUtil._
import org.jetbrains.plugins.scala.extensions
import org.jetbrains.plugins.scala.extensions.{ChildOf, ExpressionType}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScGenericCall
import org.jetbrains.plugins.scala.lang.psi.types.{Equivalence, ScType}

/**
 * @author Nikolay.Tropin
 */

class RedundantCollectionConversion(inspection: OperationOnCollectionInspection) extends SimplificationType(inspection) {
  override def hint: String = InspectionBundle.message("redundant.collection.conversion")

  override def getSimplification(single: MethodRepr): List[Simplification] = {
    val expr = single.itself match {
      case ChildOf(gc: ScGenericCall) => gc
      case ref => ref
    }
    val exprType = expr.getType().getOrAny

    (single.optionalBase, single.optionalMethodRef) match {
      case (Some(base @ ExpressionType(baseType)), Some(ref))
        if ref.refName.startsWith("to") &&
                checkResolve(ref, likeCollectionClasses) &&
                baseType.equiv(exprType) =>
        List(new Simplification(base.getText, hint, single.rightRangeInParent(single.itself)))
      case _ => Nil
    }
  }

}

class RedundantCollectionConversionInspection extends OperationOnCollectionInspection {
  override def highlightType = ProblemHighlightType.LIKE_UNUSED_SYMBOL

  override def possibleSimplificationTypes: Array[SimplificationType] = Array(new RedundantCollectionConversion(this))
}
