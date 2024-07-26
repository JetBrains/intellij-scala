package org.jetbrains.plugins.scala.codeInspection.collections

import org.jetbrains.plugins.scala.codeInspection.ScalaInspectionBundle
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression

import scala.collection.immutable.ArraySeq

class GetOrElseNullInspection extends OperationOnCollectionInspection {
  override def possibleSimplificationTypes: ArraySeq[SimplificationType] =
    ArraySeq(GetOrElseNull)
}

object GetOrElseNull extends SimplificationType {
  override def getSimplification(expr: ScExpression): Option[Simplification] = {
    expr match {
      case qual`.getOrElse`(literal("null")) =>
        Some(replace(expr).withText(invocationText(qual, "orNull")).highlightFrom(qual))
      case _ => None
    }
  }

  override def hint: String = ScalaInspectionBundle.message("getOrElse.null.hint")
}
