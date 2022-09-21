package org.jetbrains.plugins.scala.codeInspection.collections

import org.jetbrains.plugins.scala.codeInspection.ScalaInspectionBundle
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression

import scala.collection.immutable.ArraySeq

class OptionToStringInspection extends OperationOnCollectionInspection {
  override def possibleSimplificationTypes: ArraySeq[SimplificationType] = ArraySeq(OptionToStringInspection)
}

object OptionToStringInspection extends SimplificationType {

  override def hint: String = ScalaInspectionBundle.message("option.mkString.hint")

  private val mkString = """mkString"""

  override def getSimplification(expr: ScExpression): Option[Simplification] =
    getToStringToMkStringSimplification(expr, isOption, mkString, replace)
}