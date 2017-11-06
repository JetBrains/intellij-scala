package org.jetbrains.plugins.scala.codeInspection.collections

import org.jetbrains.plugins.scala.codeInspection.InspectionBundle
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression

/**
  * @author t-kameyama
  */
class OptionWithConstantInspection extends OperationOnCollectionInspection {
  override def possibleSimplificationTypes: Array[SimplificationType] = Array(OptionWithConstant)
}

object OptionWithConstant extends SimplificationType {
  override def hint: String = InspectionBundle.message("replace.with.some")

  override def getSimplification(expr: ScExpression): Option[Simplification] = expr match {
    case `scalaOption`(literal(constant)) if constant != "null" =>
      Some(replace(expr).withText(s"Some($constant)").highlightFrom(expr))
    case _ => None
  }
}