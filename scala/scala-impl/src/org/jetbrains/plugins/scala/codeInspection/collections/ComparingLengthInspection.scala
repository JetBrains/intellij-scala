package org.jetbrains.plugins.scala.codeInspection.collections

import org.jetbrains.plugins.scala.codeInspection.InspectionBundle
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression

/**
  * @author t-kameyama
  */
class ComparingLengthInspection extends OperationOnCollectionInspection{
  override def possibleSimplificationTypes: Array[SimplificationType] = Array(ComparingLength)
}

object ComparingLength extends SimplificationType {
  override def hint: String = InspectionBundle.message("replace.with.lengthCompare")

  override def getSimplification(expr: ScExpression): Option[Simplification] = {
    (expr match {
      case qual `.sizeOrLength` () `>` n if isSeq(qual) => Some(qual, n, ">")
      case qual `.sizeOrLength` () `==` n if isSeq(qual) => Some(qual, n, "==")
      case qual `.sizeOrLength` () `!=` n if isSeq(qual) => Some(qual, n, "!=")
      case qual `.sizeOrLength` () `<` n if isSeq(qual) => Some(qual, n, "<")
      case _ => None
    }).map { case (qual, n, op) =>
      replace(expr).withText(s"${invocationText(qual, "lengthCompare", n)} $op 0").highlightFrom(qual)
    }
  }
}