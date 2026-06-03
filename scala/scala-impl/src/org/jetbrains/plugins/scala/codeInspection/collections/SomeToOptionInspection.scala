package org.jetbrains.plugins.scala.codeInspection.collections

import org.jetbrains.plugins.scala.codeInspection.ScalaInspectionBundle
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression

import scala.collection.immutable.ArraySeq

final class SomeToOptionInspection extends OperationOnCollectionInspection {
  override def possibleSimplificationTypes: ArraySeq[SimplificationType] = ArraySeq(SomeToOptionInspection)
}

object SomeToOptionInspection extends SimplificationType {
  override def hint: String = ScalaInspectionBundle.message("replace.with.option")

  override def getSimplification(expr: ScExpression): Option[Simplification] = expr match {
    case `scalaSome`(e) => Some(replace(expr).withText(s"Option(${e.getText})").highlightFrom(expr))
    case _ => None
  }
}
