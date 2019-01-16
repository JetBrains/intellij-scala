package org.jetbrains.plugins.scala.codeInspection.collections

import org.jetbrains.plugins.scala.codeInspection.InspectionBundle
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression

/**
  * @author t-kameyama
  */
class SomeToOptionInspection extends OperationOnCollectionInspection {
  override def possibleSimplificationTypes: Array[SimplificationType] = Array(SomeToOptionInspection)
}

object SomeToOptionInspection extends SimplificationType {
  override def hint: String = InspectionBundle.message("replace.with.option")

  override def getSimplification(expr: ScExpression): Option[Simplification] = expr match {
    case `scalaSome`(e) => Some(replace(expr).withText(s"Option(${e.getText})").highlightFrom(expr))
    case _ => None
  }
}