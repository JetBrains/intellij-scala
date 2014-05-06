package org.jetbrains.plugins.scala
package codeInspection.collections

import org.jetbrains.plugins.scala.codeInspection.InspectionBundle
import org.jetbrains.plugins.scala.codeInspection.collections.OperationOnCollectionsUtil._
import scala.Some

/**
 * Nikolay.Tropin
 * 2014-05-06
 */
class ExistsEqualsInspection extends OperationOnCollectionInspection {
  override def possibleSimplificationTypes: Array[SimplificationType] =
    Array(new ExistsEquals(this))
}

class ExistsEquals(inspection: OperationOnCollectionInspection) extends SimplificationType(inspection) {
  override def getSimplification(single: MethodRepr) = {
    single.itself match {
      case MethodRepr(_, _, Some(ref), Seq(arg))
        if ref.refName == "exists" && checkResolve(ref, likeCollectionClasses) =>

        isEqualsWithSomeExpr(arg).toList.flatMap { expr =>
          createSimplification(single, single.itself, Seq(expr), "contains")
        }
      case _ => Nil
    }
  }

  override def hint = InspectionBundle.message("exists.equals.hint")
}
