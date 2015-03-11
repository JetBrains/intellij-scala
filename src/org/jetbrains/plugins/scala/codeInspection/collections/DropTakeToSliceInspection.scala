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

  override def getSimplification(expr: ScExpression): Option[Simplification] = expr match {
    case qual`.drop`(m)`.take`(n) =>
      Some(replace(expr).withText(invocationText(qual, "slice", m, plusOne(n))).highlightFrom(qual))
    case _ => None
  }

  private def plusOne(expr: ScExpression): ScExpression = {
    def plusOneText(lit: ScLiteral) = {
      lit.getValue match {
        case int: java.lang.Integer => (int.intValue() + 1).toString
        case long: java.lang.Long => (long.longValue() + 1).toString
        case _ => s"${expr.getText} + 1"
      }
    }
    val text = expr match {
      case lit: ScLiteral => plusOneText(lit)
      case x`+`(y: ScLiteral) => s"${x.getText} + ${plusOneText(y)}"
      case (x: ScLiteral)`+`y=> s"${plusOneText(x)} + ${y.getText}"
      case _ => s"${expr.getText} + 1"
    }
    ScalaPsiElementFactory.createExpressionFromText(text, expr).asInstanceOf[ScExpression]
  }
}