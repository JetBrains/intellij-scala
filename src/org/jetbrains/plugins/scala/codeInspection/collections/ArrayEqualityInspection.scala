package org.jetbrains.plugins.scala.codeInspection.collections

import org.jetbrains.plugins.scala.codeInspection.InspectionBundle
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression

/**
 * @author Nikolay.Tropin
 */
class ArrayEqualityInspection extends OperationOnCollectionInspection {
  override def possibleSimplificationTypes: Array[SimplificationType] = Array(ArrayEquality)
}

object ArrayEquality extends SimplificationType {
  override def hint: String = InspectionBundle.message("replace.equals.with.sameElements.for.array")

  override def getSimplification(expr: ScExpression): Option[Simplification] = expr match {
    case MethodRepr(_, Some(left), Some(ref), Seq(right)) if Set("==", "equals").contains(ref.refName) && arraysOrSeqAndArray(left, right) =>
      Some(replace(expr).withText(invocationText(left, "sameElements", right)).highlightElem(ref.nameId))
    case MethodRepr(_, Some(left), Some(ref), Seq(right)) if ref.refName == "!=" && arraysOrSeqAndArray(left, right) =>
      Some(replace(expr).withText(invocationText(negation = true, left, "sameElements", right)).highlightElem(ref.nameId))
    case _ => None
  }


  private def arraysOrSeqAndArray(left: ScExpression, right: ScExpression) = {
    isArray(left) && (isArray(right) || isSeq(right)) ||
            isArray(right) && isSeq(left)
  }
}
