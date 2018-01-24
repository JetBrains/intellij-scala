package org.jetbrains.plugins.scala.codeInspection.collections

import org.jetbrains.plugins.scala.codeInspection.InspectionBundle
import org.jetbrains.plugins.scala.codeInspection.collections.ComparingLengthInspection._
import org.jetbrains.plugins.scala.lang.psi.api.base.ScIntLiteral
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression

/**
  * @author t-kameyama
  */
class ComparingLengthInspection extends OperationOnCollectionInspection{
  override def possibleSimplificationTypes: Array[SimplificationType] = Array(ComparingLength)
}

object ComparingLengthInspection {
  val hint: String = InspectionBundle.message("replace.with.lengthCompare")

  private val ComparingLength: SimplificationType = new SimplificationType() {
    override def hint: String = ComparingLengthInspection.hint

    override def getSimplification(e: ScExpression): Option[Simplification] = Some(e).collect {
      case q `.sizeOrLength` () `>` n => (q, ">", n)
      case q `.sizeOrLength` () `>=` n => (q, ">=", n)
      case q `.sizeOrLength` () `==` n => (q, "==", n)
      case q `.sizeOrLength` () `!=` n => (q, "!=", n)
      case q `.sizeOrLength` () `<` n => (q, "<", n)
      case q `.sizeOrLength` () `<=` n => (q, "<=", n)
    } filter { case (q, _, n) =>
      isNonIndexedSeq(q) && !isZero(n)
    } map { case (q, op, n) =>
      replace(e).withText(s"${invocationText(q, "lengthCompare", n)} $op 0").highlightFrom(q)
    }
  }

  private def isZero(e: ScExpression): Boolean = e match {
    case ScIntLiteral(0) => true
    case _ => false
  }
}