package org.jetbrains.plugins.scala
package codeInspection
package collections

import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.types.api.ParameterizedType
import org.jetbrains.plugins.scala.lang.psi.types.result._
import org.jetbrains.plugins.scala.lang.psi.types.{ScType, ScTypeExt}

import scala.collection.immutable.ArraySeq

class ToSetAndBackInspection extends OperationOnCollectionInspection {
  override def possibleSimplificationTypes: ArraySeq[SimplificationType] = ArraySeq(ToSetAndBackToDistinct)
}

object ToSetAndBackToDistinct extends SimplificationType {
  override def hint: String = ScalaInspectionBundle.message("replace.toSet.and.back.with.distinct")

  override def getSimplification(expr: ScExpression): Option[Simplification] = {

    def sameCollectionType(tp1: ScType, tp2: ScType): Boolean = {
      (tp1, tp2) match {
        case (ParameterizedType(des1, _), ParameterizedType(des2, _)) if des1.equiv(des2) => true
        case _ => false
      }
    }

    expr match {
      // TODO infix notation?
      case `.toCollection`(`.toSet` (qual@Typeable(qualType)), _*)
        if sameCollectionType(qualType, expr.`type`().getOrAny) && (isSeq(qual) || isArray(qual)) =>
        Some(replace(expr).withText(invocationText(qual, "distinct")).highlightFrom(qual))
      case _ => None
    }
  }

}
