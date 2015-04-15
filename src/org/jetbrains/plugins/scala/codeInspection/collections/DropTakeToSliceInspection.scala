package org.jetbrains.plugins.scala.codeInspection.collections

import org.jetbrains.plugins.scala.codeInspection.InspectionBundle
import org.jetbrains.plugins.scala.lang.psi.api.base.ScLiteral
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory

/**
 * @author Nikolay.Tropin
 */
class DropTakeToSliceInspection extends OperationOnCollectionInspection {
  override def possibleSimplificationTypes: Array[SimplificationType] = Array(DropTakeToSlice)
}

object DropTakeToSlice extends SimplificationType {
  override def hint: String = InspectionBundle.message("replace.drop.take.with.slice")
  val takeDropHint = InspectionBundle.message("replace.take.drop.with.slice")

  override def getSimplification(expr: ScExpression): Option[Simplification] = expr match {
    case qual`.drop`(m)`.take`(n) =>
      Some(replace(expr).withText(invocationText(qual, "slice", m, sum(m, n))).highlightFrom(qual))
    case qual`.take`(n)`.drop`(m) =>
      Some(replace(expr).withText(invocationText(qual, "slice", m, n)).highlightFrom(qual).withHint(takeDropHint))
    case _ => None
  }

  private def sum(left: ScExpression, right: ScExpression): ScExpression = {
    val sumText = (left, right) match {
      case (intLiteral(l), intLiteral(r)) => s"${l + r}"
      case (intLiteral(a) `+` q, intLiteral(b)) => s"${q.getText} + ${a + b}"
      case (intLiteral(a), intLiteral(b) `+` q) => s"${q.getText} + ${a + b}"
      case (q `+` intLiteral(a), intLiteral(b)) => s"${q.getText} + ${a + b}"
      case (intLiteral(a), q `+` intLiteral(b)) => s"${q.getText} + ${a + b}"
      case (q, intLiteral(b)) => s"${q.getText} + $b"
      case (intLiteral(a), q) => s"${q.getText} + $a"
      case _ => s"${left.getText} + ${right.getText}"
    }
    ScalaPsiElementFactory.createExpressionFromText(sumText, left.getManager)
  }

  object intLiteral {
    def unapply(expr: ScExpression): Option[Int] = {
      expr match {
        case l: ScLiteral =>
          l.getValue match {
            case int: java.lang.Integer => Some(int)
            case _ => None
          }
        case _ => None
      }
    }
  }
}