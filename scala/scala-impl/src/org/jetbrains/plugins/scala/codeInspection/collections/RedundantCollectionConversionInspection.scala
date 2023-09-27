package org.jetbrains.plugins.scala.codeInspection.collections

import org.jetbrains.plugins.scala.codeInspection.ScalaInspectionBundle
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.types.ScTypeExt
import org.jetbrains.plugins.scala.lang.psi.types.result._

import scala.collection.immutable.ArraySeq

object RedundantCollectionConversion extends SimplificationType {
  override def hint: String = ScalaInspectionBundle.message("redundant.collection.conversion")

  override def getSimplification(expr: ScExpression): Option[Simplification] = {
    val typeAfterConversion = expr.`type`().getOrAny

    // note:
    // will match <Seq(1, 2).to> and <Seq(1, 2).to[List]> but not <Seq(1, 2).to>[List]
    // because of a check in MethodRepr in `.toCollection`
    expr match {
      // TODO infix notation?
      case `.toCollection`(base@Typeable(baseType)) if baseType.conforms(typeAfterConversion) =>
        val simplification = replace(expr).withText(base.getText).highlightFrom(base)
        Some(simplification)
      case _ => None
    }
  }
}

final class RedundantCollectionConversionInspection extends OperationOnCollectionInspection {
  override def possibleSimplificationTypes: ArraySeq[SimplificationType] = ArraySeq(RedundantCollectionConversion)
}
