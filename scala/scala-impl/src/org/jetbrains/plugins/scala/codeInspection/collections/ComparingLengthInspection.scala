package org.jetbrains.plugins.scala.codeInspection.collections

import org.jetbrains.plugins.scala.codeInspection.InspectionBundle
import org.jetbrains.plugins.scala.codeInspection.collections.ComparingLengthInspection._
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression

/**
  * @author t-kameyama
  */
class ComparingLengthInspection extends OperationOnCollectionInspection{
  override def possibleSimplificationTypes: Array[SimplificationType] = Array(ComparingLength)
}

private object ComparingLengthInspection {
  private val ComparingLength: SimplificationType = new SimplificationType() {
    override def hint: String = InspectionBundle.message("replace.with.lengthCompare")

    override def getSimplification(e: ScExpression): Option[Simplification] = Some(e).collect {
      case q `.sizeOrLength` () `>` n => (q, ">", n)
      case q `.sizeOrLength` () `>=` n => (q, ">=", n)
      case q `.sizeOrLength` () `==` n => (q, "==", n)
      case q `.sizeOrLength` () `!=` n => (q, "!=", n)
      case q `.sizeOrLength` () `<` n => (q, "<", n)
      case q `.sizeOrLength` () `<=` n => (q, "<=", n)
    } filter { case (q, op, n) =>
      isSeq(q) && !isIndexedSeq(q)
    } map { case (q, op, n) =>
      replace(e).withText(s"${invocationText(q, "lengthCompare", n)} $op 0").highlightFrom(q)
    }
  }
}